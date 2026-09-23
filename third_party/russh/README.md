# Russh source record

NovaScale uses Russh for SSH and `russh-sftp` for SFTP. No libssh2 code or
binary is used.

## Pinned crates

### russh

- Source: <https://github.com/Eugeny/russh>
- Crate: `russh 0.62.2`
- Source commit recorded by the crate: `c4be19f1915c8682f4615c3fd50008512b474491`
- Crate archive SHA-256: `ca6c3c1dd0a9ad3a9915a2f6e907d158f9a7e9ac32ddc772b003faafc027d9a8`
- License: Apache-2.0; see `LICENSE`
- Cargo features: defaults disabled; `ring` and `rsa` enabled

The `ring` backend is selected over the default `aws-lc-rs` backend for the
MVP because it has a smaller established Android native-build surface. RSA is
retained for compatibility with existing OpenSSH keys and hosts. Compression
is intentionally disabled for the initial release.

### russh-sftp

- Source: <https://github.com/AspectUnk/russh-sftp>
- Crate: `russh-sftp 2.3.0`
- Source commit recorded by the crate: `dcc0c06a2aa14da96fb453f05b530c8fffba1b1a`
- Crate archive SHA-256: `9ed8949eca4163c18a8f59ff96d32cf61e9c13b9735e21ef32b3907f4aafa1a9`
- License: Apache-2.0; see `LICENSE`

## Modifications and boundary

The crates are resolved by Cargo and are not vendored or modified. NovaScale's
adapter lives in `native/nova-core-rust`. It adopts the one-shot tailnet file
descriptor, supplies it to `russh::client::connect_stream`, enforces explicit
host-key verification, and exposes product-level SSH/SFTP operations through a
small JNI boundary.

## Update verification

For every update, run Rust unit tests, build both Android ABIs with NDK r28c,
inspect 16 KiB ELF alignment, and exercise none/password/private-key auth,
host-key first use/change, PTY resize, cancellation, and SFTP read/write on
device.
