# Roadmap

Ideas and loose ends that are not done yet.

## Planned

- **Refresh feeds so new episodes show up.** Episodes are fetched only when you subscribe, so a
  podcast never gets new ones. Needs a refresh when opening a podcast, pull-to-refresh, and
  ideally a periodic background refresh. This is the most important gap.
- **Text size setting.** Offer *Compact*, *Regular* and *Large* for the lists, like Gmail's display
  density. The episode lists currently use one compact size, defined in one place
  (`EpisodeListStyle` in `ui/Screens.kt`), so a setting only has to swap those values.
- **Play the next episode automatically.** Right now playback stops at the end of an episode. A
  small version (advance to the next episode of the same podcast) may not need a full queue.
- **Queue** ("up next") and **starred episodes**, as in most podcast apps.
- **Offline transcription.** Transcripts currently go through the phone's default speech
  recognizer, which is Google's speech service and may use the network. A bundled offline model
  would keep everything on the device.

## Small stuff

- Highlight the right bottom tab while viewing a transcript.
- Show a description on the page of a podcast you already follow.
- A mini player bar so playback can be controlled from any screen.
