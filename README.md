# PlayTorrio TV

A free Android TV streaming app. Built for the couch — designed to be driven entirely with a remote, no touchscreen nonsense.

If this app makes your TV better, **smash the ⭐ button at the top of this page**. It costs you nothing and it genuinely helps the project get noticed.

---

## What it does

PlayTorrio TV finds movies and shows from a stack of online sources, plays them back with a proper TV-grade player, and keeps track of what you're watching. No ads, no accounts, no subscriptions.

## Features

### Streaming
- Pulls from multiple scrapers in parallel (RgShows, VidFun, and more)
- Smart source ranking — best quality first, dead links pushed down
- Source picker so you can swap mid-watch if a stream chokes
- Auto-fallback when a source dies
- Quality labels (4K / 1080p / 720p) where the source provides them

### Torrents
- Built-in torrent streaming (no waiting for a full download)
- Live speed and peer count while you stream
- Magnet support
- Background prefetch so the next episode is ready when you are

### Player
- ExoPlayer
- Real subtitle support — multiple tracks, style customization, sizing, colors
- Multi-audio track switching
- Aspect ratio cycling (Fit / Fill / 16:9 / 4:3 / Stretch)
- Hardware decoding when the device supports it
- Resume from where you left off, automatically
- DPAD-friendly controls — every button is reachable from the remote

### Skip & Next Episode
- Auto-detect intros, recaps, credits, and previews
- One-button skip when a marker fires
- Next Episode button shows alongside skip markers and near the end of the runtime
- Both buttons stack so you can pick either with the DPAD
- Auto-play next episode when credits roll (if you want it)

### Discovery
- Trending, popular, and top-rated rows for movies and shows
- Search across all sources
- Genre browsing
- Trailer playback right from the details screen
- Cast and crew info, ratings, runtime

### Library
- Watchlist
- Continue Watching row that actually works
- Per-show episode tracking
- Multiple profiles on the same device

### TV-first UI
- Built with Jetpack Compose for TV
- 10-foot interface — large text, high contrast, readable from the couch
- Full DPAD focus handling, no dead ends
- Works on cheap Android TV boxes, sticks, and proper TVs

### In-app updater
- Checks GitHub releases on launch
- Picks the right APK for your device's CPU automatically
- Update popup steals focus so you can't miss it
- Two-button choice: Update Now or Later
- Downloads and installs without leaving the app

---

## Install

1. Download the latest APK from [Releases](https://github.com/ayman708-UX/PlayTorrioTVKT/releases)
2. Pick the one that matches your device:
   - `app-arm64-v8a-release.apk` — most modern devices (recommended)
   - `app-armeabi-v7a-release.apk` — older / cheaper boxes
3. Sideload it (Downloader app, USB, or however you usually do it)
4. Allow installs from unknown sources when prompted
5. Open and pick a profile

After the first install, the app updates itself.

---

## Build from source

You need Android Studio (or just the command-line SDK) and a JDK 17+.

```bash
git clone https://github.com/ayman708-UX/PlayTorrioTVKT.git
cd PlayTorrioTVKT
./gradlew assembleRelease
```

Signed APKs land in `app/build/outputs/apk/release/`.

### Signing your own builds

The release config expects a keystore at the repo root called `release.jks`. Generate one with:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias yourkey
```

Then update the `signingConfigs` block in [`app/build.gradle.kts`](app/build.gradle.kts) with your alias and passwords. **Do not commit `release.jks`** — it's already in `.gitignore`.

---

## Tested on

- Google TV / Chromecast with Google TV
- Nvidia Shield
- Fire TV Stick (sideloaded)
- Generic Android TV boxes
- Phones and tablets running Android 6+

---

## Contributing

Pull requests are welcome. Bug reports too — the more detail the better. If you're adding a new scraper, follow the pattern in `app/src/main/java/com/playtorrio/tv/data/scrapers/`.

---

## A note on legality

PlayTorrio TV doesn't host content. It indexes publicly available sources. What you stream is on you. Use a VPN, know your local laws, and don't be a jerk.

---

## Credits

- Player core forked from [NuvioTV](https://github.com/tapframe/NuvioTV)
- TMDB for metadata
- All the scraper authors whose work made this possible

---

## License

See [LICENSE](LICENSE).

---

### One more time — if you use this app, **star the repo**. It actually helps. ⭐
