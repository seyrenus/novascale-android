# Android architecture

NovaScale uses one in-process Tailscale node for its Android features. Kotlin
coordinates the application lifecycle and exposes the node through a
`TailnetBackend` interface. The Go package in `native/nova-tailnet-go/` owns
the `tsnet.Server` and provides login, peer discovery, dialing, and local
proxy operations. Its versioned Tailscale dependency is pinned in `go.mod`.

SSH and SFTP run in the Rust library at `native/nova-core-rust/`. Kotlin hands
each dialed full-duplex file descriptor to Rust once; network payloads do not
pass repeatedly through Kotlin or JNI. The Rust core uses `russh` and
`russh-sftp`, applies the same strict host-key policy to terminal and file
connections, and owns terminal state through the Zig adapter for
`libghostty-vt` in `native/nova-ghostty-zig/`.

The Android `app/` module uses Compose for application screens. `terminal-ui/`
contains the GPLv3 Termux-derived Canvas renderer, input handling, and
selection UI. It renders batched Ghostty snapshots rather than maintaining a
second terminal emulator. `core-api/` defines the boundaries between the app,
terminal UI, and native protocols.

The browser and optional external clients use an authenticated local proxy
bound to the device loopback interface. Tailscale destinations use the shared
node; public internet destinations use the node's ordinary network dial path.
Browser proxy credentials and saved SSH secrets are encrypted using keys
protected by Android Keystore. The app keeps its tailnet state in private
storage and disables upstream remote diagnostic log uploads.

The repository includes build scripts and dependency locks for the Go, Rust,
Zig, Gradle, and Android components. `third_party/` records upstream pins and
adaptations, and `THIRD_PARTY_NOTICES.md` lists bundled licenses. Play
submission records, service-account credentials, and upload signing material
are maintained outside this public source repository.
