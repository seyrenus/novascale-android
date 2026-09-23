# AndroidX Media3 source record

NovaScale uses AndroidX Media3 ExoPlayer and its Android View controls for
local playback of user-requested SFTP audio and video previews. Remote bytes
are first streamed by the Rust SFTP core into the application's private
temporary cache; Media3 does not receive a tailnet URL or perform preview
network I/O.

## Pinned artifacts

- Source: <https://github.com/androidx/media>
- Maven version and tag: `1.10.1`
- Tag commit: `5fb306449733dd71595700c1227ad6087578c559`
- License: Apache-2.0; see `../Apache-2.0.txt`
- `media3-exoplayer-1.10.1.aar` SHA-256:
  `19090f829afbbb595d67618f59c212f72b433ac01dc72b47594cf42552c8ed61`
- `media3-ui-1.10.1.aar` SHA-256:
  `ceb6df6e096df142865760d06439baa878aa0dead4eaa7b5fb5e4c30d1589392`
- `media3-common-1.10.1.aar` SHA-256:
  `39686f54ff6baf8cbc841199afd55619533eaef2ab6cd0efc32b8a3c55dd88c3`

The artifacts are resolved from Google Maven and are not modified or
vendored. NovaScale's adapter is
`app/src/main/java/cc/galaxnet/novascale/files/SftpFilePreviewScreen.kt`.
It creates a player only while a completed preview is visible, pauses at
`ON_STOP`, and releases the player when the preview leaves composition.

## Update verification

For every update, record the source commit and AAR hashes, review license,
release, codec, API-level, and shrinker changes, and exercise local MP3/Opus
audio plus MP4/WebM video on every supported Android API and ABI. Confirm
playback stops in the background, corrupt content fails visibly, and closing
Preview deletes the temporary file.
