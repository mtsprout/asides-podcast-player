# Asides

A podcast player for Android built around one idea: you should be able to talk back.

Triple-tap your earbuds while an episode plays, say what's on your mind, tap once to stop. Asides
records it, pins it to that exact moment in the episode, and transcribes it. Later you can read
your asides, replay them, or jump straight back to the moment you made them. It works with the
screen off, so it fits walks, drives and chores.

> Status: an early, personal project (version 0.1.0). It works and gets daily use, but expect rough
> edges. Android only.

## What it does

- **Voice asides.** Record a spoken note from your headset button, with the phone in your pocket.
  Each aside is saved with its position in the episode and transcribed after you finish.
- **Two ways to read them.** Every episode has an **Asides** tab, and a top-level **Asides** tab
  lists everything grouped by podcast, then episode (oldest first), then aside.
- **Find podcasts.** Search the Apple Podcasts directory, browse the top 10, or paste an RSS feed
  URL. Tap a podcast to preview its description and recent episodes before subscribing.
- **A familiar player.** Playing / Details / Asides tabs, 10 s back and 30 s forward, remaining
  time, a speed picker (0.5x to 2.0x), a sleep timer (including "end of episode"), and share.
- **Episode list tools.** Oldest-first by default, sorting, hide played, mark played, and range
  selection: long-press one episode, scroll, long-press another to select everything between.
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
but not yet tested on a real wired headset). A recording also stops
on its own after 60 seconds. After a double- or triple-tap, Asides ignores the stray play/pause
some earbuds add for about a second.

## Requirements

- Android 8.0 (API 26) or newer.
- Transcription needs Android 13 or newer. On older versions your asides are still recorded and
  playable, just without text.

## Build and install

You need JDK 17 or newer and the Android SDK (platform 35).

```sh
git clone <this repository>
cd <this repository>
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
- **New episodes don't appear yet.** A podcast's episodes are fetched once, when you subscribe;
  feeds are not refreshed afterwards. Refreshing is the next thing to build.
- There is no queue yet: when an episode ends, playback stops.
- Transcripts depend on your phone's recognizer and are not always accurate.

See [ROADMAP.md](ROADMAP.md) for what's planned.

## Built with

Kotlin, Jetpack Compose and Material 3, Room, Media3 (ExoPlayer and MediaSession), OkHttp and
Coil.

## License

[MIT](LICENSE)
