# Asides

A podcast player for Android built around one idea: you should be able to talk back.

Triple-tap your earbuds while an episode plays, say what's on your mind, tap once to stop. Asides
records it, pins it to that exact moment in the episode, and transcribes it. Later you can read
your asides, or jump straight back to the moment you made them. It works with the
screen off, so it fits walks, drives and chores.

> Status: **1.0.10**. Everything described here works and gets daily use. It is a young app, so you may
> still hit rough edges; please [open an issue](https://github.com/mtsprout/asides-podcast-player/issues)
> if you do. Android only.

## What it does

- **Voice asides.** Record a spoken note from your headset button, with the phone in your pocket.
  Each aside is saved with its position in the episode and transcribed after you finish. Once
  the transcript is in, the recording is deleted to save space (Settings → *Save audio files* keeps
  them instead, at about 2 MB per minute); if transcription fails, the recording is kept so nothing
  is lost.
- **Two ways to read them.** Every episode has an **Asides** tab, and a top-level **Asides** tab
  lists everything grouped by podcast, then episode (oldest first), then aside.
- **Hear an aside in context.** Tap an aside's timestamp, in an episode's Asides tab or in the global
  Asides tab, and that episode plays from ten seconds before the note. Your place isn't touched: a
  bar shows where you were, and **Return** (or the reviewed episode ending) puts you back there,
  paused. Nothing is marked played and no position is saved for the episode you reviewed.
- **Search, fix, share.** The Asides tab has a search box (every word must match the note, episode or
  podcast). Open an aside's transcript to correct it (a corrected transcript is never re-transcribed)
  or share it; the tab's buttons share, or save as a Markdown file, everything shown.
- **A front page that starts where you left off.** A *Continue listening* card with a play button,
  an *Up next* row (each podcast's next unplayed episode, new ones first) and your most recent aside
  (tap it to hear it in context).
- **Find podcasts by interest.** Discover shows the Top 5 overall, then the Top 5 in five
  categories: the ones you pick with the category chips (a random five if you pick more), topped up with random ones (new picks each
  time the app opens). *See all* opens the full chart, 25 at a time, 200 at most. You can also
  search the Apple Podcasts directory or paste an RSS feed URL. Tap a podcast to preview its
  description and recent episodes before subscribing.
- **Downloads.** Tap the arrow beside any episode to save it on your phone; it plays offline from then
  on. A ring shows progress, a check marks a finished download, and tapping the check removes it (your
  asides and place are kept). Select several episodes and choose **Download** (or **Remove**, when
  they are all downloaded) to do them together. Settings → Downloads shows the space used, can **delete
  all downloads** at once, and has a switch (on by default) that deletes an episode's download once you
  finish it or mark it played.
- **Picks up where you left off, in the car too.** Playback survives swiping the app away (the status-bar
  chip and notification stay), and a Bluetooth play button wakes Asides even if Android stopped it. An
  optional Settings switch, *Resume when Bluetooth audio connects*, starts your last episode when your
  car or earbuds connect.
- **Chapters.** When a podcast publishes a chapters file (the Podcasting 2.0 standard), the player shows the
  current chapter and a Chapters button to jump between them. Chapters stored inside the audio file are not read.
- **Android Auto.** Asides shows up on the car's head unit with its playback controls. Tested on a 2023
  Toyota RAV4.
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
- **Move subscriptions in or out (OPML).** Settings → *Subscriptions (OPML)* exports the podcasts you
  follow as a standard OPML file, and imports one from another podcast app (only podcasts you don't
  follow yet are added).
- **Per-podcast settings.** The sliders icon on a podcast's page sets, for that podcast only: its own
  playback speed, a skip-intro length (new episodes start that many seconds in; ones you've started
  carry on where you left off), and whether new episodes are downloaded automatically on Wi-Fi (the
  newest three per refresh).
- **Play queue.** Select episodes on a podcast's page and tap the queue icon: they go to the top of the
  queue and play ahead of the usual next-unplayed-episode rule, across podcasts. The queue icon at the
  top left of the Library opens it (drag the handle to reorder, tap to play now). Podcasts you don't follow
  have an add-to-queue button on their preview page; the episode is saved quietly, with the podcast kept
  out of your Library.
- **Back up and restore.** In Settings, *Back up…* saves your asides (transcripts, plus any
  recordings that were kept), your subscriptions, played marks, play queue and per-podcast settings to one file you can keep anywhere: Drive,
  Downloads, an email to yourself. *Restore…* on this or another phone adds only what's missing, so
  it's safe to do twice. This is the way to keep your asides if you uninstall or change phones.
- **Delete with care.** The trash icon on an aside lets you delete just the recording (if it was
  kept) and keep the transcript, or delete both.
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

**Google Play.** Asides is on Google Play, but only as a closed test for now: you have to sign up as a
tester before the Play Store will show it to you. To be added, [open an issue](https://github.com/mtsprout/asides-podcast-player/issues)
and ask. Play versions can lag a little behind the releases here.

**Or sideload it.** Grab the newest APK from the [Releases page](https://github.com/mtsprout/asides-podcast-player/releases)
(the one at the top; betas are marked *Pre-release*) and install it yourself.

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
  kept, just without text.

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
- Episodes stream unless you download them. Downloads are manual unless you turn on Wi-Fi auto-download for a podcast.
- *Resume when Bluetooth audio connects* applies to every Bluetooth audio device, and works only if
  Android lets an app start playing from the background; that can differ by phone.
- **Background refresh timing is up to Android.** "About every 4 hours" is a target, not a promise,
  and some phones (Samsung's battery saver, for example) postpone background work for apps you
  haven't opened in a while. Opening the app or pulling down to refresh always works.
- Notifications need the notification permission (Android 13+), which Asides asks for on first
  launch.
- Transcripts depend on your phone's recognizer and are not always accurate.

See [ROADMAP.md](ROADMAP.md) for what's planned.

## Built with

Kotlin, Jetpack Compose and Material 3, Room, Media3 (ExoPlayer and MediaSession), WorkManager,
OkHttp and Coil.

## License

[MIT](LICENSE)
