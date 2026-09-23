# Tailscale source record

NovaScale embeds Tailscale through its own Go package in
`native/nova-tailnet-go`. It does not use the separate
`tailscale/libtailscale` project.

## Pinned module

- Source: <https://github.com/tailscale/tailscale>
- Module: `tailscale.com v1.102.4` (stable channel verified 2026-09-21)
- Tag commit: `bbcd7d1fc2054b9189ebc1531acf74bd880ca0c8`
- Module checksum: `h1:FcAkb7MgfFFUIUASg18Mv5cAiraxb+eMgOQaOKqKyPo=`
- go.mod checksum: `h1:47bv91Xbg4K1p5wti7F1dmKvUVWV5BXF78d9EWJ+d6c=`
- NovaScale compiler: standard Go `1.26.6`, pinned in `native/nova-tailnet-go/go.toolchain`; matches the release's Go requirement
- Upstream Tailscale compiler-fork reference: `7275f792d406d3c386cc807937a45a4a7b699d42` from the release's `go.toolchain.rev` (recorded for comparison; NovaScale uses the standard compiler above)
- AAR build: `ts_omit_cachenetmap`, `android/arm64,android/amd64`, API 28, external linking with `max-page-size=16384`
- Generated compiler/module/input/AAR checksums: `app/libs/nova-tailnet.aar.buildinfo.txt`, emitted by `scripts/build-tailnet-aar.sh`
- License: BSD-3-Clause; see `LICENSE`
- Use: `tsnet.Server`, LocalAPI/IPN state, peer discovery, and tailnet dialing
- Modifications: none to upstream module source

The module is resolved by Go modules and is not vendored in this repository.
`go.mod` and `go.sum` are the release inputs.

## Android integration reference

- Source: <https://github.com/tailscale/tailscale-android>
- Reference commit: `0f542bd8cd40668cf751402e0ea8b1936cc34699`
- License: BSD-3-Clause; see `LICENSE`
- Studied files:
  - `Makefile`
  - `android/src/main/java/com/tailscale/ipn/App.kt`
  - `android/src/main/java/com/tailscale/ipn/NetworkChangeCallback.kt`
  - `libtailscale/interfaces.go`
  - `libtailscale/ifaceparse/ifaceparse.go`

NovaScale independently owns its small gomobile API and uses the public
`tsnet` API rather than copying the official VPN backend. The network-interface
JSON schema and conversion approach in these NovaScale files are adapted from
the reference above:

- `app/src/main/java/cc/galaxnet/novascale/tailnet/AndroidNetworkInterfaces.kt`
- `native/nova-tailnet-go/interfaces.go`

NovaScale's version caches Android's public `NetworkInterface` snapshot on the
Go side, updates it on non-VPN connectivity callbacks, and omits all device VPN,
DNS, policy, and hardware-attestation integration. Source headers and this
record preserve the upstream attribution.

## Update verification

When updating Tailscale:

1. Select an official stable even-minor release and record its tag commit and
   Go checksums here.
2. Run Go tests and regenerate the AAR for `android/arm64` and
   `android/amd64`.
3. Verify 16 KiB ELF segment alignment and APK packaging.
4. Smoke-test fresh interactive login, persisted login, peer discovery,
   tailnet dialing, network changes, logout, and custom control URLs on device.
