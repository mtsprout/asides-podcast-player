# Putting Asides on Google Play

Everything needed for the Play Console, collected in one place. Items marked **YOU** need the
maintainer's own Google account or a decision; the rest is ready.

## 0. Blocking decisions and one-time setup (YOU)

1. **Developer account.** A one-time US$25 fee plus identity verification at
   <https://play.google.com/console>. A *personal* account created after Nov 2023 must run a **closed
   test with at least 12 opted-in testers for 14 days in a row** before production access is
   unlocked; an *organization* account does not. (This rule has changed before; check the Play
   Console's own text.)
2. **Signing key. This one matters: it protects the data of everyone who sideloaded an earlier build,
   including the maintainer's own phone.** Android only lets an update replace an install if both
   are signed by the same key. The sideloaded APKs are signed with the release key whose fingerprint
   is in the README. Choose *Play App Signing* when creating the app, and **upload the existing key
   to Google as the app signing key** (Play Console → Setup → App signing → "Use a different key /
   export and upload", done with Google's PEPK tool). If Google generates a new key instead, a Play
   install will not update over a sideloaded one: the only way across is uninstall (which deletes the
   app's data) and reinstall, after taking a backup. Decide before the first upload; it cannot be
   changed afterwards.
3. **Package name** `com.asides.app` is permanent once uploaded.
4. **Contact email** for the store listing (public). Not in this repo.
5. **Version.** Play needs a final, non-beta `versionName` (suggest `1.0.0`) and a higher
   `versionCode` than 8. Not changed yet.

## 1. What is ready

- **App Bundle:** `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`
  (about 6 MB, signed with the release key, **targets API 36**, which Google requires for new apps
  since 31 Aug 2026). Rebuild after any change and after the version bump.
- **Privacy policy:** [`PRIVACY.md`](PRIVACY.md). The Play Console needs a public URL:
  `https://github.com/mtsprout/asides-podcast-player/blob/main/PRIVACY.md`.
- **Permissions in the manifest:** `INTERNET`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `VIBRATE`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MICROPHONE`.

## 2. Store listing text

**App name** (30 max): `Asides: Podcast Player`

**Short description** (80 max): `Talk back to your podcasts. Voice notes, pinned to the moment, transcribed.`

**Full description** (4000 max):

> Asides is a podcast player with one big idea: you should be able to talk back.
>
> While an episode plays, triple-press your earbuds' button, say what's on your mind, and press once
> to stop. Asides saves your voice note at that exact moment in the episode and turns it into text.
> It works with the screen off, so it suits walks, drives and chores.
>
> **Voice asides**
> - Record from your headset button, with the phone in your pocket.
> - Each aside is pinned to its position in the episode and transcribed.
> - Read them in an episode's Asides tab, or in one list of everything, grouped by podcast.
> - Tap an aside's time to hear it in context, ten seconds early, without losing your place. A bar
>   offers Return, which puts you back where you were, paused.
> - Recordings are deleted once transcribed to save space (or keep them, in Settings).
>
> **A player that stays out of the way**
> - Search Apple Podcasts, or paste any feed URL.
> - Pick up where you left off, with a Continue listening card and an Up next row.
> - Next unplayed episode starts by itself, even with the screen off.
> - 10 s back, 30 s forward, speed 0.5x to 2x, a sleep timer, and lock-screen controls.
> - Mark episodes played, select ranges, hide played, sort either way.
> - Discover shows by category, with Top 5 charts and the categories you pick.
> - Optional notifications for new episodes, and background refresh.
>
> **Yours to keep**
> No account, no ads, no analytics. Your data stays on your phone. Back up and restore your asides,
> subscriptions and played marks to a file you control.
>
> Transcription uses your phone's speech recognizer (usually Google's, which may send the recording
> to Google to transcribe it). Open source under the MIT license.

**Category:** Music & Audio. **Tags:** podcasts, audio player. **Contact:** the maintainer's email,
plus <https://github.com/mtsprout/asides-podcast-player> as the website.

## 3. Graphics (YOU, or ask for help)

- **App icon 512 x 512 PNG.** Easiest: Android Studio → *Image Asset* → check "Play Store icon" (the
  project's adaptive icon is `res/mipmap-anydpi-v26/ic_launcher.xml`).
- **Feature graphic 1024 x 500.** Needed; not made yet.
- **Phone screenshots, 2 to 8** (1080 x 2340 works). Suggested: the front page, an episode's
  player, the Asides tab (with a transcript), the review bar, Discover. **Take them from a copy with
  public test podcasts, not a phone with real asides.**

## 4. App content forms

- **Privacy policy URL:** see section 1.
- **Ads:** none. **Target audience:** adults (18+); not designed for children.
- **Content rating questionnaire:** no violence or sexual content; users can't communicate with each
  other; lets users stream third-party podcasts (user-chosen) which may contain mature content. Expect
  a Teen or Everyone-with-mature-user-content style rating; answer honestly in the form.
- **News app / government / financial / health:** none of these.
- **Data safety.** Draft answers, to be checked by the maintainer:
  - *Collects or shares data?* The app has no server of its own. Voice recordings and transcripts are
    processed and stored **on the device**. The system speech recognizer (a separate Google
    component) may send audio to Google for transcription, so the conservative answer is to declare
    **Audio (voice or sound recordings): shared with Google, for app functionality, optional** and
    to link the privacy policy that explains it.
  - *Search terms* go to Apple's iTunes services: declare **Search history/queries** collected,
    for app functionality, not linked to identity.
  - *Encrypted in transit:* the app's own requests use HTTPS where the server supports it
    (the manifest allows cleartext because some podcast hosts only serve plain HTTP audio).
  - *Can users request deletion?* Yes: delete asides in the app, or uninstall.
- **Foreground service declarations** (App content → Foreground service permissions):
  - `mediaPlayback`: playing podcast audio with the screen off and from the notification and lock
    screen. Interruption stops playback.
  - `microphone`: recording a voice aside while an episode plays, started by the user from a headset
    button with the screen off; the recording stops after 60 s or when the user ends it. **A short demo
    video link is required (YOU: screen-record a triple-press, speak, stop, with the notification
    visible; upload unlisted to YouTube).**
- **Sensitive permission `RECORD_AUDIO`:** core function; reflected in the privacy policy and the
  video above.
- **Notifications:** `POST_NOTIFICATIONS` for playback controls and optional new-episode alerts.

## 5. Release path

1. Create the app in the Play Console (app name above; free; app).
2. Enable Play App Signing with the existing key (section 0, item 2).
3. Complete the forms in section 4 and the listing in sections 2 and 3.
4. Upload the AAB to **Internal testing** first (instant, up to 100 testers) and install from the
   Play link on the maintainer's phone to check it over the sideloaded copy.
5. If the account is personal: **Closed testing** with 12+ testers for 14 days, then *Apply for
   production*. Otherwise go straight to production.
6. Production: roll out (staged rollout at 20% is a sensible start).

## 6. Before the first upload

- Back up the real phone's data (Settings → *Back up…*) and keep the file elsewhere.
- Smoke-test the API 36 build on a side-by-side copy (`-PappIdSuffix=.reltest`) before installing it
  over the real app. **Not yet done for the API 36 change.**
- Choose the final `versionName`/`versionCode`, rebuild, and keep the matching mapping file
  (`app/build/outputs/mapping/release/mapping.txt`) for crash reports.
