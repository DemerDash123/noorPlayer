# Noor Player (مشغل نور)

A lightweight Android video player (about 240 KB) for Android 7.0 and newer. It uses only the phone's built-in media framework, with no external libraries.

**Download:** [`release/NoorPlayer-1.2.apk`](release/NoorPlayer-1.2.apk) (version 1.2)

## Features

- **Library:** every video on the phone and SD card, with thumbnails, search, sorting (date, name, size, length), a Folders view and a Recent tab.
- **Resume:** remembers where each video stopped (saved every 5 seconds and whenever you leave), with a "Continue watching" bar and watched-progress bars. It also recognises the same video opened through a different link (for example from WhatsApp or the Files app).
- **Background playback:** the sound keeps playing when you leave the app or turn off the screen, with a notification (back, play/pause, forward, close) and lock-screen controls.
- **Volume-key pause:** press Volume Up and Volume Down together to pause or play, on screen, with the screen off, or from another app.
- **Open from anywhere:** "Open with" and Share from Files, WhatsApp, Telegram and other apps, plus direct links (MP4, MKV, WebM, HLS `.m3u8`).
- **Gestures:** double-tap the sides to skip; swipe on the left for brightness and on the right for volume; swipe sideways to seek; pinch to zoom.
- **Playback:** speed from 0.25x to 3x, audio track selection, volume boost, repeat (one or all), auto-play next and a sleep timer.
- **Subtitles:** built-in tracks or `.srt`/`.vtt` files, with size and timing controls. Old Arabic subtitle files (Windows-1256) are detected and shown correctly.
- **Screen:** fit, fill, stretch, 16:9, 4:3 and 100% modes; rotation that follows the video; a touch lock; picture-in-picture with play/pause/skip buttons; frame screenshots.
- **Languages:** English and Arabic (right-to-left).
- **Crash reporter:** after an unexpected close, the next launch shows the error with Copy and Share buttons.

## Install

1. Download `release/NoorPlayer-1.2.apk` on your phone and open it.
2. When asked, allow installing apps from that source.
3. If Play Protect warns that the app is unknown, tap **More details → Install anyway**.
4. Open Noor Player and allow access to your videos.

## Changes

- **1.2:** pressing both volume keys together now resumes playback too (on screen and in the background), not just pauses.
- **1.1:** background playback with a notification; pause/play with both volume keys; more reliable resume.
- **1.0:** first release.

## Project layout

| Path | What it is |
|------|------------|
| `src/com/ahmed/noorplayer/` | Android code: library screen, player, thumbnails, settings |
| `src/com/ahmed/noorplayer/core/` | Plain-Java logic (subtitles, resume, sorting, zoom math, links), unit-tested on the JVM |
| `src/com/ahmed/noorplayer/PlaybackService.java` | Foreground service and media notification for background playback |
| `test/` | JUnit 4 tests for the core logic |
| `res/` | Layouts, icons, theme, and English + Arabic strings |
| `tools/build.sh` | Builds and signs the APK |
| `tools/run_tests.sh` | Runs the unit tests |
| `tools/check.py` | Static checks: view IDs and casts, translations, manifest, API usage |
| `tools/gen_icons.py` | Generates the icons and launcher images |

## Build

Building only needs Ubuntu's packaged Android tools; Gradle is not used.

```bash
sudo apt-get install aapt android-framework-res android-sdk-platform-23 \
    dalvik-exchange zipalign apksigner dexdump junit4 openjdk-21-jdk
./tools/run_tests.sh      # unit tests
./tools/build.sh          # writes build/NoorPlayer.apk
python3 tools/check.py    # static checks (run after building)
```

- **Signing:** `build.sh` signs with `keystore/noor.jks`. If that file is missing, it creates a new key.
- **Updates:** to install an update over an existing copy of the app, build it with the **same** key. The key is kept out of this repository on purpose; keep your copy private.
- **Target:** minimum SDK 24 (Android 7.0), target SDK 34.
