# Post-1.0 plan: front page and finding podcasts by interest

Status as of 1.0.4 (2026-10-15). The first round shipped in beta4; beta5 added downloads, resume from
Bluetooth and a stopped player; what is left is below.

## Shipped in 1.0.0-beta4

The Library tab (the front page) now reads, top to bottom:

1. **Continue listening**: the episode you were last in, with artwork, minutes left, a progress bar and
   a play/pause button. Tapping the card opens the player.
2. **Up next**: each followed podcast's next unplayed episode, new ones first (shown when there are at
   least two to choose between).
3. **Most recent aside**: one card, the start of the note (about 30 characters), tap to hear it in
   context (the Return bar brings you back to your place).
4. **Your podcasts**: the tiles, with "N new" pills.
5. **Discover**: the Top 5 overall, then the Top 5 in five categories, each a sideways row with
   **See all**. Categories you pick with the chips come first (a random five if you pick more than
   five), random ones fill the rest to five (four if none are picked); the random picks change each
   time the app opens. The category chips are at the bottom; tapping one picks or unpicks it.
6. **See all** opens the full chart, 25 at a time with **Show more**, up to 200.

Also shipped: the Asides lists show the start of each transcript beside its timestamp, under a
one-line, smaller episode heading with no date; picked categories are saved in backups.

Decisions behind it: no new bottom tab (the bar stays Library and Asides); Top 10 is a drill-down, not
an inline list; categories come from Apple's top-level podcast categories through the same iTunes chart
endpoint the app already used (`toppodcasts/genre=<id>`), so there is no new dependency, account or key.

## Shipped in 1.0.4

- **Mini player bar**: a slim bar above the tabs (title, podcast, play/pause, progress) on every screen
  except the episode's own, so playback can be controlled anywhere. Tap it to open the episode.
- **Suggestions that learn from what you follow**: a "Because you follow N <category> podcasts" row at
  the top of Discover, from the category most of your followed podcasts share, leaving out shows you
  already follow. The categories come from each feed's own `itunes:category` tags and are kept in a small
  preferences file (`genres`), not the database, so there is no schema change and nothing to migrate.
  Feeds followed before this are read once in full to learn their categories.
- **Search inside a category**: the category page (See all) has a search box. Apple's search can't filter
  by category, so 200 results are fetched and filtered by category (with the subcategory table in
  `Discover`).
- **Up next** no longer repeats the episode on the Continue listening card.

## Still to do

- **Topic search** ("forgiveness", "Roman history") across all categories. Apple's search mostly matches
  titles, so this likely means a second directory. The **Podcast Index** (free, open, needs an API key)
  is acceptable to the maintainer. Cost: a key that can't be committed in the clear to a public repo,
  another third-party dependency, and a privacy note in the README.
- ~~Retry per row~~: done, each Discover row has a Retry button.
- ~~Text sizes~~: Library and player checked at 1.5x system font scale; layouts hold.

## Open questions

- Is the Top 5 row count right (5 per category), or should rows be longer?
- Should the category chips live higher on the page once a user has picked some?

## Risks and things to protect

- **The user's real data.** Anything touching Room needs a migration tested on a side-by-side copy and
  a backup first. Front-page work so far needed no schema change.
- **Unofficial iTunes endpoints.** The charts endpoint could change. Each row degrades on its own (it
  shows "Couldn't load this one."); the rest of the page keeps working.
- **Light and dark mode**, both, and no flash at launch.
- **Text size.** The new cards haven't been checked at the Regular and Large list sizes.
- **Tests.** The pure parts are unit tested (`Discover`: transcript snippet, interests, up next, which
  categories to show). Chart parsing is not tested against saved samples yet.
