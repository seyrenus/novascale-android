# Bundled terminal fonts

NovaScale fetches four immutable, checksum-verified font files into a generated
Android resource directory. The font binaries are not copied from the iOS
reference tree.

| Display name | Immutable source | File SHA-256 | License and notice |
| --- | --- | --- | --- |
| JetBrains Mono | JetBrains/JetBrainsMono `cd5227bd1f61dff3bbd6c814ceaf7ffd95e947d9` (`v2.304`), `fonts/ttf/JetBrainsMono-Regular.ttf` | `a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f` | Copyright 2020 The JetBrains Mono Project Authors; SIL OFL 1.1 |
| JetBrains Mono Nerd Font Mono | ryanoasis/nerd-fonts `fa7b859994228a9c8759f99c55a8d31ee92a1b5e` (`v3.4.0`), patched JetBrains Mono regular/mono | `f01031f40e48dc29e1112e6b0b0450a2c6cd097f3f35cfff05c55cb311f8034c` | JetBrains Mono notice plus Copyright 2014 Ryan L McIntyre; patched fonts under SIL OFL 1.1; Nerd Fonts tooling under MIT |
| Fira Code | tonsky/FiraCode `eee6db993696aba61ff4eef03698e2987d79910c` (`6.2`), release archive `Fira_Code_v6.2.zip` | archive `0949915ba8eb24d89fd93d10a7ff623f42830d7c5ffc3ecbf960e4ecad3e3e79`; TTF `5992ab9640e2df491b2f609467b1de60e8bc39b2c28db184342a0592d98f6117` | Copyright 2014 The Fira Code Project Authors; SIL OFL 1.1 |
| Source Code Pro | adobe-fonts/source-code-pro `803b7e23ec97ae58b6232ea76519a76d428ba268`, `TTF/SourceCodePro-Regular.ttf` | `74bd80d3e42a08517cd7e1108ba3d86f2da29ac0f3065be95e0357956ab9db37` | Copyright 2023 Adobe, Reserved Font Name “Source”; SIL OFL 1.1 |

The complete SIL Open Font License is in [`OFL-1.1.txt`](OFL-1.1.txt). The
Nerd Fonts MIT tooling notice is in [`NERD-FONTS-MIT.txt`](NERD-FONTS-MIT.txt).
The reproducible fetch recipe is `scripts/fetch-terminal-fonts.sh`.
