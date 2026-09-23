# Android Tree-sitter source record

NovaScale uses Android Tree-sitter through Sora Editor for incremental syntax
highlighting. It does not use Tree-sitter for LSP, completion, or code
intelligence.

## Pinned binding

- Source: <https://github.com/AndroidIDEOfficial/android-tree-sitter>
- Maven version: `4.3.2`
- Source commit: `797150222de664181e31be53e9a5b253056ba9b0`
- Bundled Tree-sitter commit:
  `ca7d15add61bf77391d387d26441599b96c0eb9c`
- License: LGPL-2.1; see `../LGPL-2.1.txt`
- `android-tree-sitter-4.3.2.aar` SHA-256:
  `28130fca9819cff858f76295f2e7ff72c5b1f983f8d249fcc08696d512921e6f`

Upstream now marks the binding project unmaintained. NovaScale therefore pins
the exact working release, treats dependency replacement as a future audit
item, and requires Android/NDK and 16 KiB alignment verification for any
update or replacement.

## Pinned grammars

| Grammar | Source commit | AAR SHA-256 | License |
| --- | --- | --- | --- |
| C | `371fd0bf0650581b6e49f06f438c88c419859696` | `57e55d9860c0d478bd2f1667b0b5c4b8da9dd80ad34d946d1fb3e322350ba37a` | MIT |
| C++ | `e0c1678a78731e78655b7d953efb4daecf58be46` | `993ca2c19665bc7e5eb986bb0cf946d0aefc2f801eb7853f051a5f24d063bbcf` | MIT |
| Java | `b882ba058034c200be5c392d1275941fe64fe216` | `2c426857b45205afee0da992283cf8734829896257fa8b8f4aad186d584fe2d2` | MIT |
| JSON | `3b129203f4b72d532f58e72c5310c0a7db3b8e6d` | `aec3ccf940b3a430f0bb7c7522cd0a24eb1084c8967e04bf1972a59bf7a7a85e` | MIT |
| Kotlin | `6590e9ba746f3042a5c23611a266a934c3d3289b` | `e9f81947e86b07d6443fd592b27c704aca071233ee9e078d8a53dd6d01db2832` | MIT |
| Properties | `cb60fd8d09d8d7adcde6da30c42f18513335651f` | `df2f6844f68fe6c0663ea09d5bb5a9c754ef14561ac220644ba0958884a891d0` | LGPL-2.1 |
| Python | `b8a4c64121ba66b460cb878e934e3157ecbfb124` | `a8c7cdbd620faffc3f68fb711d80dcc17e77a08fafa53e98083959298bef4836` | MIT |
| XML | `a1e5891a40ad79ca476d48ad687a3f304f8ee735` | `af57faabbfdc76ca85c60354727d591f0f0b89aef2bd4029a8d74270c49ba898` | LGPL-2.1 |

The exact MIT grammar notices are preserved in `GRAMMAR_LICENSES.md`; LGPL
grammar terms are in `../LGPL-2.1.txt`. The Tree-sitter core MIT notice is in
`../MIT-tree-sitter.txt`.

NovaScale imports only each pinned grammar's `queries/highlights.scm` under
`app/src/main/assets/tree-sitter-queries/`. The C++ query is composed after the
C query, and the Kotlin query omits an unsupported `#any-of?` predicate. Those
small changes remain reviewable in the application source. Grammar native
libraries are otherwise the unmodified Maven AAR contents.

## Update verification

For every update, verify source/submodule commits and hashes, compile all
queries, load each grammar on both Android ABIs, confirm syntax colors on a
device, and rerun the APK ABI, ELF LOAD, and 16 KiB zip-alignment checks.

