# Post-1.0 plan: front page and finding podcasts by interest

Status as of 1.0.0 (2026-10-14). The first round shipped in beta4; beta5 added downloads, resume from
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

## Still to do

- **Mini player bar**, so playback can be controlled from any screen. Pairs naturally with the
  Continue listening card.
- **Suggestions that learn from what you follow.** The iTunes lookup returns each show's genres, so
  subscribed podcasts could seed suggestions ("Because you follow 2 religion podcasts"). This needs the
  genre stored on `Podcast`, which is a Room schema change: it needs a tested migration, a backup taken
  first, and a check on a side-by-side copy (never on the real app).
- **Topic search** ("forgiveness", "Roman history"). Apple's search mostly matches titles, so this
  likely means a second directory. The **Podcast Index** (free, open, needs an API key) is acceptable
  to the maintainer. Cost: a key that can't be committed in the clear to a public repo, another
  third-party dependency, and a privacy note in the README. It would also hedge against Apple's
  unofficial chart endpoint disappearing.
- **Search inside a category** (iTunes search accepts a genre hint; verify before relying on it).
- **Up next** can show the same episode as the Continue card; filter it out.
- **Retry per row.** A Discover row that fails to load just says it couldn't.

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
