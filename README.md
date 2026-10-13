# Asides

A podcast player for Android built around one idea: you should be able to talk back.

Triple-tap your earbuds while an episode plays, say what's on your mind, tap once to stop. Asides
records it, pins it to that exact moment in the episode, and transcribes it. Later you can read
your asides, replay them, or jump straight back to the moment you made them. It works with the
screen off, so it fits walks, drives and chores.

> Status: **beta** (1.0.0-beta1). Everything described here works and gets daily use, but it is new
> enough that you may hit rough edges. Please [open an issue](https://github.com/mtsprout/asides-podcast-player/issues)
> if you do. Android only.

## What it does

- **Voice asides.** Record a spoken note from your headset button, with the phone in your pocket.
  Each aside is saved with its position in the episode and transcribed after you finish.
- **Two ways to read them.** Every episode has an **Asides** tab, and a top-level **Asides** tab
  lists everything grouped by podcast, then episode (oldest first), then aside.
- **Find podcasts.** Search the Apple Podcasts directory, browse the top 10, or paste an RSS feed
  URL. Tap a podcast to preview its description and recent episodes before subscribing.
- **A familiar player.** Playing / Details / Asides tabs, 10 s back and 30 s forward, « to jump to
  the start of the episode and » to skip to the next one (the same five controls are on the
  lock-screen card and notification), remaining
  time, a speed picker (0.5x to 2.0x), a sleep timer (including "end of episode"), and share.
- **Episode list tools.** Oldest-first by default, sorting, hide played, mark played, and range
  selection: long-press one episode, scroll, long-press another to select everything between.
- **Plays on by itself.** When an episode ends, the next *unplayed* episode in the podcast's list
  order starts automatically, so a series flows from one day to the next. Mark a block as played
  and it skips straight past it. The player shows "Up next", and you can turn this off in the
  player's More menu. The sleep timer's "end of episode" still stops playback.
- **New episodes show up.** Asides checks your podcasts when you open it, when you pull down to
  refresh (on the home page or a podcast's page), and in the background about every 4 hours.
  Unchanged feeds are skipped cheaply. New episodes get a blue dot, and the home page shows
  an "N new" badge on the podcast.
- **Optional notifications.** Tap the bell on a podcast's page to be notified when it has a new
  episode. They are off by default, one notification per podcast, and tapping one opens the episode.
- **Back up and restore.** In Settings, *Back up…* saves your asides (with their recordings and
  transcripts), your subscriptions and your played marks to one file you can keep anywhere: Drive,
  Downloads, an email to yourself. *Restore…* on this or another phone adds only what's missing, so
  it's safe to do twice. This is the way to keep your asides if you uninstall or change phones.
- **Text size.** Settings has Compact, Regular and Large for the episode lists.
- **A welcome note** on the home page explains the earbud gesture to a first-time user (Settings can
  show it again).
- **Unsubscribing is safe.** It removes a podcast from your library but keeps its episodes and
  your asides. Subscribe again and everything comes back.

## Headset gestures

| Gesture | Action |
| --- | --- |
| Single press | Play / pause |
| Double press | Skip forward 30 seconds |
| Triple press | Pause and start recording an aside |
| Any press while recording | Stop and save the aside, then resume playback |

Headsets differ in which buttons they send. Many Bluetooth earbuds send *previous track* for a
triple-tap and *next track* for a double-tap, and Asides handles those the same way. Wired
headsets send the same key for every press, so Asides counts the presses (this path is written
but not yet tested on a real wired headset; see the [roadmap](ROADMAP.md)). A recording also stops
on its own after 60 seconds. After a double- or triple-tap, Asides ignores the stray play/pause
some earbuds add for about a second.

## Download

Grab the newest APK from the [Releases page](https://github.com/mtsprout/asides-podcast-player/releases)
(the one at the top; betas are marked *Pre-release*) and install it yourself ("sideloading").
Asides is not on the Play Store.

1. On your phone, download `Asides-<version>.apk` from the release.
2. Open the file. Android will ask to let your browser (or Files app) install apps; allow it for
   this one install. If Play Protect warns about an unrecognized developer, choose **Install
   anyway**: the app is open source and you can read or build every line of it.
3. Open Asides and allow the **microphone** and **notifications** when asked.

Every release is signed with the same key, so later releases install over earlier ones and keep
your subscriptions and asides. Sideloaded apps don't update themselves; come back to the Releases
page when you want a newer version. To check what you downloaded, compare its SHA-256 with the one
on the release page, and the signing certificate with this fingerprint:

```
SHA-256: A0:05:47:B4:90:A9:89:E4:4A:01:83:F4:FE:5A:6D:60:93:74:D8:2E:41:2A:39:BB:E3:00:5F:93:A2:71:5D:43
```

## Requirements

- Android 8.0 (API 26) or newer.
- Transcription needs Android 13 or newer. On older versions your asides are still recorded and
  playable, just without text.

## Build and install

You need JDK 17 or newer and the Android SDK (platform 35). The steps below make a debug build.
Release builds need a signing key of your own (see `app/build.gradle.kts`).

```sh
git clone https://github.com/mtsprout/asides-podcast-player.git
cd asides-podcast-player
echo "sdk.dir=/path/to/your/Android/sdk" > local.properties   # Android Studio does this for you
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and run it. Run the unit tests with
`./gradlew testDebugUnitTest`.

Asides asks for the **microphone** (to record asides) and **notifications** (for the playback
controls). Grant both on first launch. In a debug build you can re-run transcription on every
saved aside with `adb shell am start -n com.asides.app/.MainActivity --es retranscribe 1`.

## Privacy

There are no accounts and no analytics, and everything you create stays on your phone. What does
go over the network:

- **Apple's iTunes services** for search, the top-10 list and artwork, and **the podcast's own
  feed and audio** when you browse or play.
- **Transcription.** Your phone's default speech recognizer does it. On most phones that is
  Google's speech service, which may upload the audio of an aside to transcribe it. The recording
  file itself stays on your phone, but if sending the audio to Google is a problem for you,
  Asides is not yet the right tool for transcripts.

## Known limitations

- The Top podcasts list uses an older, unofficial Apple endpoint that could stop working.
- Episodes are streamed, not downloaded, so playback needs a connection.
- **Background refresh timing is up to Android.** "About every 4 hours" is a target, not a promise,
  and some phones (Samsung's battery saver, for example) postpone background work for apps you
  haven't opened in a while. Opening the app or pulling down to refresh always works.
- Notifications need the notification permission (Android 13+), which Asides asks for on first
  launch.
- There is no queue yet: automatic play simply follows the podcast's list order.
- Transcripts depend on your phone's recognizer and are not always accurate.

See [ROADMAP.md](ROADMAP.md) for what's planned.

## Built with

Kotlin, Jetpack Compose and Material 3, Room, Media3 (ExoPlayer and MediaSession), WorkManager,
OkHttp and Coil.

## License

[MIT](LICENSE)
