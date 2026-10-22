# Frequently asked questions

Short answers for new users. For the full feature list see the [README](README.md); for the
details of what is stored and sent, see the [privacy policy](PRIVACY.md).

## The basics

### What is Asides?

A podcast player for Android built around talking back. While an episode plays, you triple-press
your earbuds' button, say what's on your mind, and press once to stop. Asides saves the note
("an aside") pinned to that exact moment in the episode and turns it into text.

### How do I record an aside?

Triple-press your headset button. Playback pauses and recording starts. Press once more to stop:
the aside is saved and the episode resumes. A recording also stops by itself after 60 seconds.
It works with the screen off and the phone in your pocket.

### What do the other headset presses do?

Single press is play/pause, double press skips forward 30 seconds. Many Bluetooth earbuds send
*previous track* for a triple-tap and *next track* for a double-tap; Asides treats those the same
way. See [Headset gestures](README.md#headset-gestures).

### Where do I find my asides?

Every episode has an **Asides** tab, and the top-level **Asides** tab lists all of them grouped by
podcast, then episode, then aside. Tap an aside's timestamp to hear the episode from ten seconds
before the note; **Return** puts you back where you were, and nothing is marked played.

### Can I use it without earbuds?

The player works as a normal podcast player without them, but recording an aside is built around
the headset button (a triple-press). Without a headset you can still listen, browse, download and
read asides you already have.

## Microphone and transcription

### Why does Asides need the microphone?

To record your asides, and for nothing else. Asides records audio only when you start an aside
(a triple-press on a headset) and stops when you finish or after 60 seconds. It never
listens in the background.

### Where does transcription happen?

On your phone's built-in speech recognizer, which Asides asks Android to run. Asides has no
server and sends nothing to its developer. But which recognizer that is depends on your phone:

- **On most phones it is Google's speech service, which may upload the aside's audio to Google**
  to transcribe it. Google's handling of that audio is covered by Google's privacy policy.
- If your phone has no standard recognizer but does have an on-device one, Asides uses that, and
  the audio stays on the phone.

The recording file itself always stays on your phone. If sending audio to Google isn't acceptable
to you, don't record asides (or turn off the recognizer in your phone's settings); an offline,
fully on-device model is on the [roadmap](ROADMAP.md).

### Does transcription work while I'm offline?

That depends on your phone's recognizer. If it needs the network and you have none, transcription
can fail. When it fails, Asides keeps the recording so nothing is lost.

### Why are some transcripts wrong?

Transcripts come from your phone's recognizer and are not always accurate, especially with names,
background noise or a noisy street. Open an aside's transcript to correct it; a corrected
transcript is never re-transcribed.

### My asides have no text. Why?

Transcription needs **Android 13 or newer**. On older versions asides are still recorded and kept,
just without text. On newer versions, a missing transcript usually means the phone had no
speech recognizer available, or recognition failed or timed out (after 60 seconds); in those cases
the recording is kept.

### Does Asides keep my recordings?

By default the recording is deleted once it has been transcribed, to save space. Turn on Settings →
*Save audio files* to keep them (about 2 MB per minute). If transcription fails, the recording is
kept either way. The trash icon on an aside lets you delete just the recording or the whole note.

## Privacy and data

### Do I need an account?

No. There are no accounts, ads or analytics.

### What does Asides send over the network?

Searches and charts go to Apple's iTunes services; feeds and audio come from each podcast's own
servers; and transcription may involve Google's speech service as described above. Everything
you create (subscriptions, play positions, asides) stays on your phone.

### Why does it ask for other permissions?

- **Notifications:** for the playback controls (and, if you turn them on, new-episode alerts).
- **Bluetooth:** only if you enable *Resume when Bluetooth audio connects*. It is used just to
  notice that a Bluetooth audio device connected; device names and addresses are not read or stored.

## Using the app

### How do I add podcasts?

Use **Discover** to browse top charts and categories, search the Apple Podcasts directory, or paste
an RSS feed URL. You can also import your subscriptions from another app with an OPML file
(Settings → *Subscriptions (OPML)*).

### Can I listen offline?

Yes. Tap the arrow beside an episode to download it. You can also auto-download new episodes on
Wi-Fi per podcast (the sliders icon on a podcast's page).

### Does it work with Android Auto?

Yes; Asides shows up on the car's head unit with its playback controls.

### How do I move to a new phone, or keep my asides if I reinstall?

Settings → *Back up…* saves your asides, subscriptions, played marks, queue and per-podcast
settings to one file you can keep anywhere. *Restore…* on the other phone adds what's missing and
is safe to run twice. Uninstalling the app deletes its data, so back up first.

### Does unsubscribing delete my asides?

No. Unsubscribing keeps the podcast's episodes and your asides; subscribe again and everything comes back.

### Why don't new episodes appear right away?

Asides checks when you open it, when you pull down to refresh, and in the background about every
4 hours. Android decides the background timing, and some phones postpone it for apps you haven't
opened lately. Pull-to-refresh always works.

### Does it work on iPhone?

No, Android only (Android 8.0 or newer).

## Getting it and getting help

### How do I install it?

Asides is on Google Play as a closed test; [open an issue](https://github.com/mtsprout/asides-podcast-player/issues)
to be added to the tester list. Or sideload the APK from the
[Releases page](https://github.com/mtsprout/asides-podcast-player/releases). See
[Download](README.md#download) for the steps.

### I found a bug, or something doesn't work with my headset.

Please [open an issue](https://github.com/mtsprout/asides-podcast-player/issues). Wired headsets in
particular are untested; reports are very welcome.
