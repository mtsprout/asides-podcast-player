# Roadmap

Ideas and loose ends that are not done yet.

## Planned

- **Queue** ("up next") and **starred episodes**, as in most podcast apps.
- **Offline transcription.** Transcripts currently go through the phone's default speech
  recognizer, which is Google's speech service and may use the network. A bundled offline model
  would keep everything on the device.

## Help wanted

- **Test wired headsets.** Wired headsets send the same key for every press, so Asides counts
  presses to tell a single, double and triple press apart (`PressCounter`, covered by unit tests).
  That path has never run on a real wired headset, because the maintainer's phone has no headphone
  jack. If you have a phone and wired headset that work together, please try the gestures and
  [open an issue](https://github.com/mtsprout/asides-podcast-player/issues) with what happened.

## Small stuff

- Scheduled automatic backups, and Android's own cloud backup for the app's data.
- Text size for the whole app (today it applies to the episode lists).
- Show when each podcast was last checked, and a "mark all as seen" for the new-episode dots.
- One setting to turn all new-episode notifications on or off.
- Highlight the right bottom tab while viewing a transcript.
- Show a description on the page of a podcast you already follow.
- A mini player bar so playback can be controlled from any screen.
