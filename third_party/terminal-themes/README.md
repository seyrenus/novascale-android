# Bundled terminal themes

Nova Dark and Nova Light are NovaScale-owned palettes. The other built-in
palettes are adapted into NovaScale's 16-color terminal model from these
immutable upstream sources:

| Theme | Immutable source | License |
| --- | --- | --- |
| Solarized Dark | `altercation/solarized` commit `62f656a02f93c5190a8753159e34b385588d5ff3` | Copyright 2011 Ethan Schoonover; MIT |
| Dracula | `dracula/dracula-theme` commit `c988d3d1c9e40dffef7c28f4b99079523637d5d2` | Copyright 2023 Dracula Theme; MIT |
| Nord | `nordtheme/nord` commit `1cef71605416a222e57225b544540ce0fcec18d4` | Copyright 2016-present Sven Greb; MIT |

NovaScale stores the palette values in
`TerminalAppearance.kt`, maps them to Ghostty's default and ANSI colors, and
uses its own cursor and selection defaults. No upstream application code is
included. The retained license notices are in [`MIT-NOTICES.txt`](MIT-NOTICES.txt).

