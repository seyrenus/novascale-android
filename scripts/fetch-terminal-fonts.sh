#!/usr/bin/env bash
set -euo pipefail

output_dir="${1:?generated Android font resource directory is required}"
# Keep archives outside the generated Android resource tree. Anything directly
# under res/ is interpreted as a resource directory by aapt2.
work_dir="${output_dir}/../../downloads"
mkdir -p "${output_dir}" "${work_dir}"

fetch() {
  local url="$1"
  local destination="$2"
  local expected="$3"
  curl --fail --location --silent --show-error --retry 3 "${url}" --output "${destination}"
  local actual
  actual="$(sha256sum "${destination}" | cut -d ' ' -f 1)"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "Font checksum mismatch for ${destination}: expected ${expected}, got ${actual}" >&2
    exit 1
  fi
}

fetch \
  "https://github.com/JetBrains/JetBrainsMono/raw/cd5227bd1f61dff3bbd6c814ceaf7ffd95e947d9/fonts/ttf/JetBrainsMono-Regular.ttf" \
  "${output_dir}/jetbrains_mono.ttf" \
  "a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f"

fetch \
  "https://github.com/ryanoasis/nerd-fonts/raw/fa7b859994228a9c8759f99c55a8d31ee92a1b5e/patched-fonts/JetBrainsMono/Ligatures/Regular/JetBrainsMonoNerdFontMono-Regular.ttf" \
  "${output_dir}/jetbrains_mono_nerd.ttf" \
  "f01031f40e48dc29e1112e6b0b0450a2c6cd097f3f35cfff05c55cb311f8034c"

fetch \
  "https://github.com/adobe-fonts/source-code-pro/raw/803b7e23ec97ae58b6232ea76519a76d428ba268/TTF/SourceCodePro-Regular.ttf" \
  "${output_dir}/source_code_pro.ttf" \
  "74bd80d3e42a08517cd7e1108ba3d86f2da29ac0f3065be95e0357956ab9db37"

fira_archive="${work_dir}/Fira_Code_v6.2.zip"
fetch \
  "https://github.com/tonsky/FiraCode/releases/download/6.2/Fira_Code_v6.2.zip" \
  "${fira_archive}" \
  "0949915ba8eb24d89fd93d10a7ff623f42830d7c5ffc3ecbf960e4ecad3e3e79"
unzip -p "${fira_archive}" ttf/FiraCode-Regular.ttf > "${output_dir}/fira_code.ttf"
actual_fira="$(sha256sum "${output_dir}/fira_code.ttf" | cut -d ' ' -f 1)"
expected_fira="5992ab9640e2df491b2f609467b1de60e8bc39b2c28db184342a0592d98f6117"
if [[ "${actual_fira}" != "${expected_fira}" ]]; then
  echo "Font checksum mismatch for Fira Code: expected ${expected_fira}, got ${actual_fira}" >&2
  exit 1
fi
