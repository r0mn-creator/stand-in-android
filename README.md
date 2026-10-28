# Stand-In for Android

Stand-In lets a small computer anywhere in your home feel like it is plugged straight into
your big PC: its screens, keyboard, mouse, pen, controllers and USB devices.

This is the Android client (phones, tablets, handhelds and TV boxes). Install it, pair with
your PC once, press Connect. Resolution, frame rate and quality are automatic; change them
in Settings if you want to.

Stand-In has three parts:
[server](https://github.com/r0mn-creator/stand-in-server) (runs on the PC),
[desktop client](https://github.com/r0mn-creator/stand-in-client) (Linux, Windows, macOS) and
[Android client](https://github.com/r0mn-creator/stand-in-android).

**Status:** Beta 2 (0.2.0). Expect rough edges.

## Building

```
./gradlew assembleNonRoot_gameDebug
```

The look of every Stand-In app comes from one design system in `stand-in-brand/design/`
(`DESIGN.md`, `tokens.json`, `generate.py`).

## Credits and licence

Stand-In is its own project. It started from open-source streaming software and has
evolved into something different, but it owes a lot to these projects and keeps their
licence, the GNU GPL v3 (see `LICENSE.txt`). Their copyright notices stay in the source.

- [Moonlight](https://moonlight-stream.org) (moonlight-qt, moonlight-android, moonlight-common-c)
- [Artemis](https://github.com/ClassicOldSong/moonlight-android) by ClassicOldSong
- [Apollo](https://github.com/ClassicOldSong/Apollo) by ClassicOldSong, itself built on
  [Sunshine](https://github.com/LizardByte/Sunshine) by LizardByte
- [Inter](https://rsms.me/inter/) typeface by Rasmus Andersson (SIL OFL)
