// NovaScale for Android
// Copyright (C) 2026 NovaScale contributors
//
// SPDX-License-Identifier: GPL-3.0-only

use crate::model::{Command, CoreEvent, StartRequest};
use crate::session::{SessionError, connect_authenticated, emit};
use base64::Engine;
use russh::Disconnect;
use russh_sftp::client::SftpSession;
use russh_sftp::protocol::FileType;
use serde_json::Value;
use serde_json::json;
use std::future::Future;
use std::os::fd::OwnedFd;
use std::sync::mpsc as std_mpsc;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

const MAX_EDIT_BYTES: usize = 2 * 1024 * 1024;

pub async fn run_sftp(
    fd: OwnedFd,
    mut request: StartRequest,
    events: std_mpsc::Sender<String>,
    mut commands: mpsc::Receiver<Command>,
    cancel: CancellationToken,
) -> Result<(), SessionError> {
    let session = connect_authenticated(fd, &mut request, &events).await?;
    let channel = session
        .channel_open_session()
        .await
        .map_err(|_| SessionError::Channel)?;
    channel
        .request_subsystem(true, "sftp")
        .await
        .map_err(|_| SessionError::Channel)?;
    let sftp = SftpSession::new(channel.into_stream())
        .await
        .map_err(|_| SessionError::Channel)?;
    sftp.set_timeout(15);
    emit(&events, CoreEvent::named("sftp_ready"));

    loop {
        tokio::select! {
            _ = cancel.cancelled() => break,
            Some(command) = commands.recv() => match command {
                Command::SftpList { request_id, path } => {
                    let response = match sftp.read_dir(path.clone()).await {
                        Ok(entries) => {
                            let entries = entries.map(|entry| {
                                let metadata = entry.metadata();
                                json!({
                                    "name": entry.file_name(),
                                    "path": entry.path(),
                                    "kind": file_type_name(entry.file_type()),
                                    "size": metadata.size,
                                    "modifiedAt": metadata.mtime,
                                    "permissions": metadata.permissions,
                                })
                            }).collect::<Vec<_>>();
                            json!({"path": path, "entries": entries})
                        }
                        Err(_) => json!({"error": "Unable to list the remote directory"}),
                    };
                    emit_payload(&events, "sftp_list", &request_id, &response);
                }
                Command::SftpRead { request_id, path } => {
                    let response = match sftp.read(path.clone()).await {
                        Ok(data) if data.len() <= MAX_EDIT_BYTES => json!({
                            "path": path,
                            "base64": base64::engine::general_purpose::STANDARD.encode(data),
                        }),
                        Ok(_) => json!({"error": "The remote file is too large to edit"}),
                        Err(_) => json!({"error": "Unable to read the remote file"}),
                    };
                    emit_payload(&events, "sftp_read", &request_id, &response);
                }
                Command::SftpWrite { request_id, path, base64 } => {
                    let response = match base64::engine::general_purpose::STANDARD.decode(base64) {
                        Ok(data) if data.len() <= MAX_EDIT_BYTES =>
                            write_atomic(&sftp, &path, &request_id, &data).await,
                        Ok(_) => json!({"error": "The edited file is too large"}),
                        Err(_) => json!({"error": "The edited file payload is invalid"}),
                    };
                    emit_payload(&events, "sftp_write", &request_id, &response);
                }
                Command::SftpCreateDirectory { request_id, path } => {
                    let response = if sftp.try_exists(path.clone()).await.unwrap_or(true) {
                        json!({"error": "A remote item with this name already exists"})
                    } else {
                        match sftp.create_dir(path.clone()).await {
                            Ok(()) => json!({"path": path, "created": true}),
                            Err(_) => json!({"error": "Unable to create the remote folder"}),
                        }
                    };
                    emit_payload(&events, "sftp_create", &request_id, &response);
                }
                Command::SftpCreateFile { request_id, path } => {
                    let response = if sftp.try_exists(path.clone()).await.unwrap_or(true) {
                        json!({"error": "A remote item with this name already exists"})
                    } else {
                        match sftp.create(path.clone()).await {
                            Ok(mut file) => match file.shutdown().await {
                                Ok(()) => json!({"path": path, "created": true}),
                                Err(_) => json!({"error": "Unable to finish creating the remote file"}),
                            },
                            Err(_) => json!({"error": "Unable to create the remote file"}),
                        }
                    };
                    emit_payload(&events, "sftp_create", &request_id, &response);
                }
                Command::SftpRename {
                    request_id,
                    source_path,
                    destination_path,
                } => {
                    let response = if sftp.try_exists(destination_path.clone()).await.unwrap_or(true) {
                        json!({"error": "A remote item with this name already exists"})
                    } else {
                        match sftp.rename(source_path.clone(), destination_path.clone()).await {
                            Ok(()) => json!({
                                "sourcePath": source_path,
                                "destinationPath": destination_path,
                                "renamed": true,
                            }),
                            Err(_) => json!({"error": "Unable to rename the remote item"}),
                        }
                    };
                    emit_payload(&events, "sftp_rename", &request_id, &response);
                }
                Command::SftpDelete {
                    request_id,
                    path,
                    directory,
                } => {
                    let result = if directory {
                        sftp.remove_dir(path.clone()).await
                    } else {
                        sftp.remove_file(path.clone()).await
                    };
                    let response = match result {
                        Ok(()) => json!({"path": path, "deleted": true}),
                        Err(_) if directory => json!({"error": "Unable to delete the remote folder. Make sure it is empty."}),
                        Err(_) => json!({"error": "Unable to delete the remote file"}),
                    };
                    emit_payload(&events, "sftp_delete", &request_id, &response);
                }
                Command::SftpUpload {
                    request_id,
                    path,
                    local_fd,
                    total_bytes,
                } => {
                    let transfer = upload_from_fd(
                        &sftp,
                        &events,
                        &request_id,
                        &path,
                        local_fd.into_owned(),
                        total_bytes,
                    );
                    match wait_for_transfer(transfer, &request_id, &mut commands, &cancel).await {
                        TransferWait::Completed(response) => {
                            emit_payload(&events, "sftp_upload", &request_id, &response);
                        }
                        TransferWait::Canceled => {
                            let _ = sftp
                                .remove_file(temporary_sibling(&path, &request_id, "upload"))
                                .await;
                            emit_payload(
                                &events,
                                "sftp_upload",
                                &request_id,
                                &json!({"path": path, "canceled": true}),
                            );
                        }
                        TransferWait::Close => {
                            let _ = sftp
                                .remove_file(temporary_sibling(&path, &request_id, "upload"))
                                .await;
                            break;
                        }
                    }
                }
                Command::SftpDownload {
                    request_id,
                    path,
                    local_fd,
                } => {
                    let transfer = download_to_fd(
                        &sftp,
                        &events,
                        &request_id,
                        &path,
                        local_fd.into_owned(),
                    );
                    match wait_for_transfer(transfer, &request_id, &mut commands, &cancel).await {
                        TransferWait::Completed(response) => {
                            emit_payload(&events, "sftp_download", &request_id, &response);
                        }
                        TransferWait::Canceled => {
                            emit_payload(
                                &events,
                                "sftp_download",
                                &request_id,
                                &json!({"path": path, "canceled": true}),
                            );
                        }
                        TransferWait::Close => break,
                    }
                }
                Command::SftpCancelTransfer { .. } => {}
                Command::Close => break,
                _ => {}
            }
        }
    }

    let _ = sftp.close().await;
    let _ = session
        .disconnect(Disconnect::ByApplication, "", "en")
        .await;
    emit(&events, CoreEvent::named("closed"));
    Ok(())
}

enum TransferWait {
    Completed(Value),
    Canceled,
    Close,
}

async fn wait_for_transfer<F>(
    transfer: F,
    request_id: &str,
    commands: &mut mpsc::Receiver<Command>,
    cancel: &CancellationToken,
) -> TransferWait
where
    F: Future<Output = Value>,
{
    tokio::pin!(transfer);
    loop {
        tokio::select! {
            response = &mut transfer => return TransferWait::Completed(response),
            _ = cancel.cancelled() => return TransferWait::Close,
            command = commands.recv() => match command {
                Some(Command::SftpCancelTransfer { request_id: canceled_id })
                    if canceled_id == request_id => return TransferWait::Canceled,
                Some(Command::Close) | None => return TransferWait::Close,
                _ => {}
            }
        }
    }
}

async fn write_atomic(sftp: &SftpSession, path: &str, request_id: &str, data: &[u8]) -> Value {
    let temporary_path = temporary_sibling(path, request_id, "edit");
    let backup_path = temporary_sibling(path, request_id, "backup");
    let result = async {
        let mut file = sftp.create(temporary_path.clone()).await.map_err(|_| ())?;
        file.write_all(data).await.map_err(|_| ())?;
        file.shutdown().await.map_err(|_| ())?;

        // Servers with overwrite-capable rename provide a truly atomic
        // replacement. SFTP v3 servers that reject an existing destination
        // use a recoverable backup swap so an edit still never truncates the
        // original in place.
        if sftp
            .rename(temporary_path.clone(), path.to_owned())
            .await
            .is_ok()
        {
            return Ok::<bool, ()>(true);
        }
        sftp.rename(path.to_owned(), backup_path.clone())
            .await
            .map_err(|_| ())?;
        if sftp
            .rename(temporary_path.clone(), path.to_owned())
            .await
            .is_err()
        {
            let _ = sftp.rename(backup_path.clone(), path.to_owned()).await;
            return Err(());
        }
        let _ = sftp.remove_file(backup_path.clone()).await;
        Ok(false)
    }
    .await;
    match result {
        Ok(atomic) => json!({"path": path, "saved": true, "atomic": atomic}),
        Err(()) => {
            let _ = sftp.remove_file(temporary_path).await;
            if !sftp.try_exists(path.to_owned()).await.unwrap_or(false) {
                let _ = sftp.rename(backup_path, path.to_owned()).await;
            }
            json!({"error": "Unable to save the remote file atomically"})
        }
    }
}

async fn upload_from_fd(
    sftp: &SftpSession,
    events: &std_mpsc::Sender<String>,
    request_id: &str,
    path: &str,
    local_fd: OwnedFd,
    total_bytes: Option<u64>,
) -> Value {
    let mut local = tokio::fs::File::from_std(std::fs::File::from(local_fd));
    let temporary_path = temporary_sibling(path, request_id, "upload");
    if sftp.try_exists(path.to_owned()).await.unwrap_or(true) {
        return json!({"error": "A remote item with this name already exists"});
    }
    let result = async {
        let mut remote = sftp.create(temporary_path.clone()).await.map_err(|_| ())?;
        let copied = copy_with_progress(
            &mut local,
            &mut remote,
            events,
            "upload",
            request_id,
            total_bytes,
        )
        .await
        .map_err(|_| ())?;
        remote.shutdown().await.map_err(|_| ())?;
        sftp.rename(temporary_path.clone(), path.to_owned())
            .await
            .map_err(|_| ())?;
        Ok::<u64, ()>(copied)
    }
    .await;
    match result {
        Ok(bytes) => json!({"path": path, "bytes": bytes, "complete": true}),
        Err(()) => {
            let _ = sftp.remove_file(temporary_path).await;
            json!({"error": "Unable to upload the selected file"})
        }
    }
}

async fn download_to_fd(
    sftp: &SftpSession,
    events: &std_mpsc::Sender<String>,
    request_id: &str,
    path: &str,
    local_fd: OwnedFd,
) -> Value {
    let mut local = tokio::fs::File::from_std(std::fs::File::from(local_fd));
    let result = async {
        let mut remote = sftp.open(path.to_owned()).await.map_err(|_| ())?;
        let total = remote
            .metadata()
            .await
            .ok()
            .and_then(|metadata| metadata.size);
        let copied = copy_with_progress(
            &mut remote,
            &mut local,
            events,
            "download",
            request_id,
            total,
        )
        .await
        .map_err(|_| ())?;
        local.flush().await.map_err(|_| ())?;
        Ok::<u64, ()>(copied)
    }
    .await;
    match result {
        Ok(bytes) => json!({"path": path, "bytes": bytes, "complete": true}),
        Err(()) => json!({"error": "Unable to download the remote file"}),
    }
}

async fn copy_with_progress<R, W>(
    reader: &mut R,
    writer: &mut W,
    events: &std_mpsc::Sender<String>,
    direction: &str,
    request_id: &str,
    total_bytes: Option<u64>,
) -> std::io::Result<u64>
where
    R: AsyncRead + Unpin,
    W: AsyncWrite + Unpin,
{
    let mut buffer = vec![0_u8; 128 * 1024];
    let mut transferred = 0_u64;
    loop {
        let count = reader.read(&mut buffer).await?;
        if count == 0 {
            break;
        }
        writer.write_all(&buffer[..count]).await?;
        transferred += count as u64;
        emit_payload(
            events,
            "sftp_transfer_progress",
            request_id,
            &json!({
                "direction": direction,
                "bytes": transferred,
                "totalBytes": total_bytes,
            }),
        );
    }
    writer.flush().await?;
    Ok(transferred)
}

fn temporary_sibling(path: &str, request_id: &str, operation: &str) -> String {
    let (parent, name) = path.rsplit_once('/').unwrap_or(("", path));
    let short_request = request_id
        .chars()
        .filter(|character| character.is_ascii_alphanumeric())
        .take(12)
        .collect::<String>();
    let parent = if parent.is_empty() { "/" } else { parent };
    format!(
        "{}/.{}.novascale-{}-{}",
        parent.trim_end_matches('/'),
        name,
        operation,
        short_request
    )
}

fn emit_payload(
    events: &std_mpsc::Sender<String>,
    event_type: &str,
    request_id: &str,
    payload: &serde_json::Value,
) {
    let mut event = CoreEvent::named(event_type);
    event.request_id = Some(request_id);
    event.payload = Some(payload);
    emit(events, event);
}

fn file_type_name(file_type: FileType) -> &'static str {
    match file_type {
        FileType::Dir => "directory",
        FileType::File => "file",
        FileType::Symlink => "symlink",
        FileType::Other => "other",
    }
}
