// NovaScale for Android
// Copyright (C) 2026 NovaScale contributors
//
// SPDX-License-Identifier: GPL-3.0-only

mod model;
mod session;
mod sftp;
mod terminal;

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{JNI_FALSE, JNI_TRUE, jboolean, jint, jlong};
use jni::{Env, NativeMethod, native_method};
use model::{API_VERSION, Command};
use session::{SessionKind, manager, start_response};
use std::os::fd::{FromRawFd, OwnedFd};

const _NATIVE_BUILD_INFO: NativeMethod = native_method! {
    java_type = "cc.galaxnet.novascale.nativecore.NativeCore",
    static extern fn native_build_info() -> JString,
};

const _NATIVE_START_SSH: NativeMethod = native_method! {
    java_type = "cc.galaxnet.novascale.nativecore.NativeCore",
    static extern fn native_start_ssh(fd: jint, request: JString) -> JString,
};

const _NATIVE_START_SFTP: NativeMethod = native_method! {
    java_type = "cc.galaxnet.novascale.nativecore.NativeCore",
    static extern fn native_start_sftp(fd: jint, request: JString) -> JString,
};

const _NATIVE_POLL_EVENT: NativeMethod = native_method! {
    java_type = "cc.galaxnet.novascale.nativecore.NativeCore",
    static extern fn native_poll_event(session_id: jlong, timeout_ms: jint) -> JString,
};

const _NATIVE_SEND_COMMAND: NativeMethod = native_method! {
    java_type = "cc.galaxnet.novascale.nativecore.NativeCore",
    static extern fn native_send_command(session_id: jlong, command: JString) -> jboolean,
};

const _NATIVE_SNAPSHOT: NativeMethod = native_method! {
    java_type = "cc.galaxnet.novascale.nativecore.NativeCore",
    static extern fn native_snapshot(session_id: jlong) -> [jbyte],
};

const _NATIVE_TERMINAL_PROBE: NativeMethod = native_method! {
    java_type = "cc.galaxnet.novascale.nativecore.NativeCore",
    static extern fn native_terminal_probe() -> [jbyte],
};

const _NATIVE_RELEASE: NativeMethod = native_method! {
    java_type = "cc.galaxnet.novascale.nativecore.NativeCore",
    static extern fn native_release(session_id: jlong),
};

fn native_build_info<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
) -> Result<JString<'local>, jni::errors::Error> {
    JString::from_str(
        env,
        format!(
            r#"{{"apiVersion":{},"rustCore":"0.1.0","russh":"0.62.2","russhSftp":"2.3.0","ghostty":"a746d0f72819","snapshotAbi":1,"crypto":"ring+rsa"}}"#,
            API_VERSION
        ),
    )
}

fn native_start_ssh<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    fd: jint,
    request: JString<'local>,
) -> Result<JString<'local>, jni::errors::Error> {
    start_native(env, fd, request, SessionKind::Shell)
}

fn native_start_sftp<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    fd: jint,
    request: JString<'local>,
) -> Result<JString<'local>, jni::errors::Error> {
    start_native(env, fd, request, SessionKind::Sftp)
}

fn start_native<'local>(
    env: &mut Env<'local>,
    fd: jint,
    request: JString<'local>,
    kind: SessionKind,
) -> Result<JString<'local>, jni::errors::Error> {
    let request = request.try_to_string(env)?;
    let response = if fd < 0 {
        start_response(Err(session::StartError::InvalidRequest))
    } else {
        // Ownership transfers at this boundary. Every success and error path
        // either moves the descriptor into the session or closes it on drop.
        let owned = unsafe { OwnedFd::from_raw_fd(fd) };
        start_response(manager().start(owned, &request, kind))
    };
    JString::from_str(env, response)
}

fn native_poll_event<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    session_id: jlong,
    timeout_ms: jint,
) -> Result<JString<'local>, jni::errors::Error> {
    let event = manager()
        .get(session_id.max(0) as u64)
        .map(|entry| entry.poll_event(timeout_ms.max(0) as u64))
        .unwrap_or_default();
    JString::from_str(env, event)
}

fn native_send_command<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    session_id: jlong,
    command: JString<'local>,
) -> Result<jboolean, jni::errors::Error> {
    let command = command.try_to_string(env)?;
    let parsed = serde_json::from_str::<Command>(&command).ok();
    let sent = parsed.is_some_and(|command| {
        manager()
            .get(session_id.max(0) as u64)
            .is_some_and(|entry| entry.send_command(command))
    });
    Ok(if sent { JNI_TRUE } else { JNI_FALSE })
}

fn native_snapshot<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
    session_id: jlong,
) -> Result<JByteArray<'local>, jni::errors::Error> {
    let snapshot = manager()
        .get(session_id.max(0) as u64)
        .map(|entry| entry.snapshot_bytes())
        .unwrap_or_default();
    let output = JByteArray::new(env, snapshot.len())?;
    let signed =
        unsafe { std::slice::from_raw_parts(snapshot.as_ptr().cast::<i8>(), snapshot.len()) };
    output.set_region(env, 0, signed)?;
    Ok(output)
}

fn native_terminal_probe<'local>(
    env: &mut Env<'local>,
    _class: JClass<'local>,
) -> Result<JByteArray<'local>, jni::errors::Error> {
    let mut terminal = terminal::TerminalState::new(12, 2, None);
    // Exercise the actual Ghostty mode parser and packed mouse ABI in the device probe.
    #[cfg(feature = "ghostty")]
    {
        for (input, expected) in [
            (b"\x1b[?1002;1006h".as_slice(), 4 | 16),
            (b"\x1b[?1002;1006l".as_slice(), 0),
            (b"\x1b[?1003;1006h".as_slice(), 8 | 16),
            (b"\x1b[?2004h".as_slice(), 8 | 16 | 256),
            (b"\x1bc".as_slice(), 0),
        ] {
            terminal.feed(input);
            let snapshot = terminal.snapshot_bytes();
            if snapshot.len() < 56
                || i32::from_le_bytes(snapshot[52..56].try_into().unwrap()) != expected
            {
                return JByteArray::new(env, 0);
            }
        }
    }
    #[cfg(feature = "ghostty")]
    {
        terminal.feed(b"\x1b]52;c;Y2xpcGJv");
        terminal.feed(b"YXJk\x07");
        if terminal.take_clipboard().as_deref() != Some("clipboard")
            || terminal.take_clipboard().is_some()
        {
            return JByteArray::new(env, 0);
        }
        terminal.feed(b"\x1b]52;c;?\x1b\\");
        if terminal.take_clipboard().is_some() || !terminal.drain_pty_writes().is_empty() {
            return JByteArray::new(env, 0);
        }
        terminal.feed(b"\x1b]52;c;%%%\x07");
        if terminal.take_clipboard().is_some() {
            return JByteArray::new(env, 0);
        }
    }
    terminal.feed(b"\x1b[1;32mGhostty\x1b[0m");
    let snapshot = terminal.snapshot_bytes();
    let output = JByteArray::new(env, snapshot.len())?;
    let signed =
        unsafe { std::slice::from_raw_parts(snapshot.as_ptr().cast::<i8>(), snapshot.len()) };
    output.set_region(env, 0, signed)?;
    Ok(output)
}

fn native_release<'local>(
    _env: &mut Env<'local>,
    _class: JClass<'local>,
    session_id: jlong,
) -> Result<(), jni::errors::Error> {
    manager().release(session_id.max(0) as u64);
    Ok(())
}
