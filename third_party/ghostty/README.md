# Ghostty source record

- Upstream: https://github.com/ghostty-org/ghostty
- NovaScale build pin: commit `a746d0f7281954eb251915f4cd9fcea4924ad999`
- Zig package hash: `ghostty-1.3.2-dev-5UdBC4aaEQVBspo81eyoKCFIEVCHBbbZxLh7jzc1kOum`
- License: MIT (retained in `LICENSE`)
- Module used: `ghostty-vt`

The current stable GUI release observed during integration was Ghostty v1.3.1
at commit `332b2aefc6e72d363aa93ab6ecfc86eeeeb5ed28`. Its installed public C header
exposes parsers and input helpers but not the full mutable terminal/render-state
surface required by an interactive SSH client. NovaScale therefore pins the
exact newer revision already exercised by Chuchu's Android bridge and isolates
it behind `native/nova-ghostty-zig`. The adapter is the only code allowed to
depend on Ghostty's evolving Zig API.

NovaScale does not import Ghostty's renderer or platform application. It uses
the VT terminal state, render-state snapshot, and terminal response generation.
Kitty image decoding is disabled in the initial Canvas-rendered MVP.

The packed snapshot's previously reserved 32-bit word at byte 52 now contains
mouse flags: bits 0–3 are X10/normal/button/any-event tracking; bits 4–7 are
SGR/UTF-8/urxvt/SGR-pixel encoding. Zero preserves old no-mouse behavior and the
56-byte header is unchanged. Mode state is read from Ghostty, not reparsed in
Kotlin. The device runtime probe checks enable, disable, and terminal reset.

Bit 8 of the same mode word carries bracketed-paste mode (DECSET 2004).
The adapter also handles Ghostty's parsed OSC 52 clipboard-write action, retaining
at most one pending base64 payload (1 MiB decoded). Rust decodes valid UTF-8 and
emits a low-frequency `clipboard_write` event. Clipboard queries are ignored;
no device clipboard content is returned to the remote host. The focused Android
terminal consumes writes; hidden/background sessions cannot set the clipboard.
