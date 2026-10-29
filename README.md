# IPTV Player

A clean, light-themed Android IPTV player built with **Kotlin** and **Jetpack Compose**.
Add your own M3U/M3U8 playlists (link or file), browse channels in a grid, and watch in a full-featured player with
picture-in-picture.

> The app ships **no channels or playlists**. You supply your own playlist links or files and are responsible for
> having the right to watch the content they point to.

## Features

**Playlists (home)**
- Add a playlist by **URL** or pick a local **M3U / M3U8 file** (⋮ menu → *Add URL* / *Select File*)
- **Long-press and drag** a playlist to reorder it; the order is saved
- Edit (✎) the name or link of a playlist, or delete it
- Floating **play button**: paste a single stream link (e.g. one `.m3u8`) and play it right away; recent links are kept
  in a **history** list (tap to replay, ✕ to remove, *Clear* to wipe)

**Channels**
- 3-column grid with round logos; channels without a logo (or whose logo fails to load) show the app logo
- **Search**, category chips (*All*, *Favorites*, then the playlist's groups)
- ♥ favorite any channel
- **Pull down to refresh** the playlist

**Player** (separate screen, black background)
- Play / pause, **previous / next channel** (shown only when there is a neighbour in the list you opened it from)
- **Double-tap** left / right side to seek **−10 s / +10 s**
- **Swipe up/down** on the left half for **brightness**, on the right half for **volume**
- Seek bar with remaining / total time (live streams show a LIVE badge)
- **Fit / Fill / Zoom** switch, **CC** toggle, mute, **lock** controls, fullscreen (rotates the screen)
- Settings: playback **speed**; **Quality** appears only when the stream really has several video qualities, **Audio**
  only when it has several audio tracks (dual audio)
- **Back** or **Home** while playing → the video floats in **picture-in-picture**; Back also returns you to the
  channel list underneath
- No background playback: the video stops when it is no longer on screen

**General**
- Light theme with a blue accent, animated splash screen, English / Bengali friendly
- Supports HLS (`.m3u8`) and regular progressive streams; plain `http` streams are allowed

## Tech stack

| Area | Library |
|------|---------|
| UI | Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 (ExoPlayer + HLS) |
| Storage | Room (playlists, favorites), SharedPreferences (stream history) |
| Network | OkHttp |
| Images | Coil |

`minSdk 24` · `targetSdk 34` · Kotlin 1.9 · AGP 8.5 · Gradle 8.9 · JDK 17

## Project structure

```
app/src/main/java/com/iptv/player/
├── MainActivity.kt        # splash + playlist / channel pages
├── PlayerActivity.kt      # video page (own activity so PiP can float over the lists)
├── PlayerManager.kt       # the single shared ExoPlayer + current channel / queue
├── MainViewModel.kt       # playlists, channels, favorites, stream history
├── Screens.kt             # all Compose screens (playlists, channels, player controls)
├── Splash.kt              # animated splash screen
├── Theme.kt               # colors
├── PlayerIcons.kt         # small vector icons used by the player
└── data/                  # Room entities / DAOs, M3U parser
```

## Build

### With GitHub Actions (no local setup)

1. Push the project to GitHub (branch `main`).
2. Open **Actions → Build APK**. Every push to `main` builds a **debug** APK.
3. Download it from the run page → **Artifacts → iptv-player-debug**.

### Locally

Requires JDK 17 and Gradle 8.9 (or generate a wrapper once with `gradle wrapper`).

```bash
gradle assembleDebug        # app/build/outputs/apk/debug/
```

## Releasing a signed APK

Release builds are signed with **your own keystore**. Do this once:

1. **Create a keystore** (keep it safe — you need the same key for every future update):
   ```bash
   keytool -genkey -v -keystore iptv-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias iptv
   ```
2. **Encode it** so it can be stored as a secret:
   ```bash
   base64 -w0 iptv-release.jks      # macOS: base64 -i iptv-release.jks
   ```
3. In the GitHub repo open **Settings → Secrets and variables → Actions → New repository secret** and add:

   | Secret | Value |
   |--------|-------|
   | `KEYSTORE_BASE64` | the base64 text from step 2 |
   | `KEYSTORE_PASSWORD` | the keystore password |
   | `KEY_ALIAS` | `iptv` (the alias you used) |
   | `KEY_PASSWORD` | the key password |

4. **Publish a release** by pushing a version tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   (or create a release with a new `v…` tag in the GitHub web UI).

The *Build APK* workflow then builds `assembleRelease`, verifies the signature with `apksigner`, and attaches
`IPTV-Player-1.0.0.apk` to a GitHub Release (it is also available as a workflow artifact).
`versionName` comes from the tag and `versionCode` from the workflow run number, so every release can update the last.

**Local signed build:** copy `keystore.properties.example` to `keystore.properties`, fill it in, run
`gradle assembleRelease`.

> ⚠️ Never commit the `.jks` file or `keystore.properties` (they are in `.gitignore`). If you lose the keystore you can
> no longer ship updates to people who installed your signed APK — back it up.
>
> Before publishing anywhere, change `applicationId` (`com.iptv.player`) in `app/build.gradle.kts` to your own id.

### বাংলায় সংক্ষেপে (সাইন করে রিলিজ)

1. `keytool` দিয়ে একবার keystore (`.jks`) বানান এবং নিরাপদ জায়গায় ব্যাকআপ রাখুন, হারালে আপডেট দেওয়া যাবে না।
2. `base64 -w0 iptv-release.jks` চালিয়ে পাওয়া লেখাটা GitHub Secret `KEYSTORE_BASE64` এ দিন; সাথে `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD` ও দিন।
3. `git tag v1.0.0` আর `git push origin v1.0.0` দিলেই Actions সাইন করা APK বানিয়ে Release এ যুক্ত করে দেবে।

## Notes & limitations

- Release builds are not minified (`isMinifyEnabled = false`) to stay safe without per-device testing; enabling R8
  needs keep-rules for Room, Media3 and Coil.
- Quality / audio choices depend on what the stream exposes. Providers that publish each quality as a separate link
  will not show a quality list.
- PiP needs Android 8.0+ and *Picture-in-picture* allowed for the app in system settings.
- Permissions: `INTERNET`, `WAKE_LOCK` (keeps the network awake while streaming).

## License

Add your license here (for example MIT).
