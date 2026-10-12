# Roadmap

Ideas and loose ends that are not done yet.

## Planned

- **Text size setting.** Offer *Compact*, *Regular* and *Large* for the lists, like Gmail's display
  density. The episode lists currently use one compact size, defined in one place
  (`EpisodeListStyle` in `ui/Screens.kt`), so a setting only has to swap those values.
- **Offline transcription.** Transcripts currently go through the phone's default speech
  recognizer, which is Google's speech service and may use the network. A bundled offline model
  would keep everything on the device.

## Small stuff

- Highlight the right bottom tab while viewing a transcript.
- Show a description on the page of a podcast you already follow.
- A mini player bar so playback can be controlled from any screen.
