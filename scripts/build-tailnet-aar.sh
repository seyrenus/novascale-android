#!/usr/bin/env bash
set -euo pipefail

android_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
module_root="$android_root/native/nova-tailnet-go"
output="${1:-$android_root/app/libs/nova-tailnet.aar}"

: "${ANDROID_SDK_ROOT:=${ANDROID_HOME:-$HOME/Android/Sdk}}"
: "${ANDROID_NDK_HOME:=$ANDROID_SDK_ROOT/ndk/28.2.13676358}"
export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT" ANDROID_NDK_HOME
export GOFLAGS="-buildvcs=false ${GOFLAGS:-}"
# Use the tested compiler, including when the host has a newer Go installation.
export GOTOOLCHAIN="$(cat "$module_root/go.toolchain")"

if [[ ! -d "$ANDROID_NDK_HOME" ]]; then
    echo "Android NDK 28.2.13676358 was not found at $ANDROID_NDK_HOME" >&2
    exit 1
fi

mkdir -p "$(dirname "$output")"
cd "$module_root"
tools_dir="$module_root/.tools/bin"
mkdir -p "$tools_dir"
go build -o "$tools_dir/gobind" golang.org/x/mobile/cmd/gobind
go build -o "$tools_dir/gomobile" golang.org/x/mobile/cmd/gomobile
export PATH="$tools_dir:$PATH"

gomobile bind \
    -target=android/arm64,android/amd64 \
    -androidapi=28 \
    -javapkg=cc.galaxnet.novascale.gobridge \
    -tags=ts_omit_cachenetmap \
    -trimpath \
    -ldflags='-linkmode=external -extldflags=-Wl,-z,max-page-size=16384' \
    -o "$output" \
    .
{
    go version
    go list -m tailscale.com golang.org/x/mobile
    echo 'targets=android/arm64,android/amd64 androidapi=28 tags=ts_omit_cachenetmap'
    echo 'linkmode=external max-page-size=16384 trimpath=true'
    sha256sum go.mod go.sum "$output"
} | tee "${output}.buildinfo.txt"
