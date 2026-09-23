# Host OS icon source record

NovaScale uses small platform-identity vectors to keep host rows consistent
with the iOS product while retaining Android-native layout and interaction.

## Simple Icons

- Source: <https://github.com/simple-icons/simple-icons>
- Commit: `c53db5666567f6469e4cc14750bb6a43a8f50964`
- License: CC0-1.0; retained in `CC0-1.0.txt`
- Imported files: `icons/android.svg` and `icons/apple.svg`
- NovaScale files: `app/src/main/res/drawable/ic_platform_android.xml`
  and `app/src/main/res/drawable/ic_platform_apple.xml`
- Modifications: converted SVG path data to Android VectorDrawable XML and
  changed the fill to white so Compose can place each mark on its semantic
  platform tile.

## Devicon

- Source: <https://github.com/devicons/devicon>
- Commit: `7330accdbc47e2dc0c19789a48533c4a3c50fe58`
- License: MIT; retained in `DEVICON-MIT.txt`
- Copyright: Copyright (c) 2015 konpa
- Imported files: `icons/linux/linux-plain.svg` and
  `icons/windows8/windows8-original.svg`
- NovaScale files: `app/src/main/res/drawable/ic_platform_linux.xml` and
  `app/src/main/res/drawable/ic_platform_windows.xml`
- Modifications: converted SVG path data to Android VectorDrawable XML and
  changed the fills to white for the semantic platform tiles.

Android, Apple, Linux, and Windows names and marks may be trademarks of their
respective owners. Their use identifies peer operating systems and does not
imply endorsement.
