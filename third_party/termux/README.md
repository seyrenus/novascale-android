# Termux terminal-view source record

- Upstream: https://github.com/termux/termux-app
- Commit: `3df69d1da197dd9bd71a3bafd902dffd720576b4`
- Upstream license: GPL-3.0-only, with upstream-noted Apache-2.0 inheritance
- Adapted files:
  - `terminal-view/src/main/java/com/termux/view/TerminalRenderer.java`
  - `terminal-view/src/main/java/com/termux/view/TerminalView.java`
  - `terminal-view/src/main/java/com/termux/view/GestureAndScaleRecognizer.java`

NovaScale's `terminal-ui/TerminalCanvasView.kt` adapts the renderer's cached
font metrics, fixed-cell horizontal correction, Canvas batching, resize math,
IME `BaseInputConnection`, Unicode/code-point handling, touch-to-focus, scroll,
and pinch-to-resize patterns. It consumes immutable libghostty-vt snapshots
rather than importing Termux's terminal emulator or session objects.

NovaScale modifications include Kotlin conversion, the Ghostty snapshot model,
Russh callbacks, Compose hosting, a compact key encoder, and removal of all
Termux application/session dependencies.

2026-09-22: NovaScale-owned xterm mouse encoding and Android touch/mouse routing
now consume Ghostty mode flags. One-finger pan sends a held-button drag when
requested by the application; two-finger translation sends wheel events.
Local selection, normal scrollback, and pinch zoom remain available. The iOS
SwiftTerm fork at `090a1d7702d1044432918773a423e1260f11ba73` was read as a behavioral reference; no SwiftTerm source was copied.
