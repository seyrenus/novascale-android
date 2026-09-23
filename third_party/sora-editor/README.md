# Sora Editor source record

NovaScale uses Sora Editor as the Android-native editing surface for remote
UTF-8 files. It does not bundle Sora's TextMate, LSP, or autocomplete modules.

## Pinned artifacts

- Source: <https://github.com/Rosemoe/sora-editor>
- Maven version: `0.24.4`
- Tag commit: `1e199e11dcbf9e6526cdcbdf1bdb25b74140496a`
- License: LGPL-2.1-or-later; see `../LGPL-2.1.txt`
- `editor-0.24.4.aar` SHA-256:
  `ee0ff49a899dc2fa7ab5dd7388e733f0c606333d54864c599644be640095a428`
- `language-treesitter-0.24.4.aar` SHA-256:
  `c8d49d2a7d3a6b3e5d98c0a6616a29335ac99d5e7e8b05a6e863fb24d0395524`

The artifacts are resolved from Maven Central and are not modified or
vendored. NovaScale's adapter is
`app/src/main/java/cc/galaxnet/novascale/files/SftpCodeEditor.kt`. It provides
the product theme, file-to-language mapping, search, undo/redo, wrapping, and
the bridge to the existing Rust-owned SFTP document state.

Every distributed source tag includes this record and the Gradle version pin.
The complete corresponding Sora source is available from the immutable commit
above. Recipients may replace or relink the LGPL library in a source build; no
obfuscation or signing restriction is used to prevent modification.

## Update verification

For every update, record the source commit and AAR hashes, review license
changes and release notes, run unit/lint/APK checks, and exercise open, edit,
undo/redo, search, wrapping, save, rotation, large-file rejection, and IME
behavior on the supported Android matrix.

