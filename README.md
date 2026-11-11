# SmartNotes

An offline Android app (Kotlin, Jetpack Compose, Room) with three parts: **Notes**, a **Tasks** checklist and **Shopping**
lists, each with reminders that work like Planner's.

## Notes

Everything "smart" in a note is read from its plain text, so the text stays the single source of truth:

| Write | You get |
|---|---|
| `#groceries` | a tag (filter by it on the Notes screen) |
| `[[Trip plan]]` or `[[Trip plan\|shown]]` | a link to the note with that title; the other note lists it under *Linked from* |
| `- [ ] buy milk` / `- [x] done` | a checkbox, ticked from the note's *Checklist* (it stays in the note; it isn't a to-do task) |

Also: full-text search (Room FTS4, word prefixes, matches highlighted), related notes (shared tags and words), and
**Share → SmartNotes** from other apps (text or a link becomes a new note; the subject is its title).
**+** opens a new note with the cursor in its text and the keyboard up.
The button left of sort (four squares) shows notes as a **grid** of cards, each as tall as its note (two columns on a
phone, more on a wider screen), with a longer preview; the list button switches back. The choice is remembered and
kept in backups. Everything else works the same in both: search, tags, pinned first, long-press to select, ≡ to drag.

## Tasks

One anytime checklist, not linked to notes. Open tasks first (high priority first, marked "!"; low greyed; newest first
within a priority), ticked ones below under *Done* (hidden until shown; can be cleared). Tap a task for its details, priority and its own
reminder: a date and time, its sound, and a repeat (every day / week / month / N days; monthly keeps its day, Jan 31 →
Feb 28 → Mar 31). Ticking a repeating task moves its reminder to the next time instead.

## Shopping

Lists are dates: *+ New date* asks for a day and opens that day's list (one per day). *Anytime* is the undated list
and can't be deleted. A dated list's ✎ changes its date or sets a reminder on that day (with its sound), or deletes it.
Dates show as Today / Tomorrow or in Settings → Date format; the top bar shows the open list's day and full
date. Type `2x milk`, `milk 2L` or `eggs x12`: the quantity shows apart from the name. Tap an item's name to rename it or change its quantity (suggestions follow the new name); a name already on that list isn't taken twice. Past items pop up under the
box while typing, the typed letters in bold (tap one to add it, ✕ forgets it), and an item already on the list isn't added twice. The reminder lists what is still to buy.
Shopping opens on the list you looked at last; the first time, or if that list is gone, on today's (made if needed).
The chips run Anytime, Today, Tomorrow (those three always first, in that order), then the days to come, and the
row scrolls to the list shown. Lists whose day has gone
are under the *Past (n)* chip: it lists them newest first with what each still has to buy, and a tap opens one (its
chip shows while it is open). The 🔍 at the right of the add box searches (for what is typed, if anything) the items of every list in use, past ones too (not the
recycle bin); results are grouped by list, ticked ones marked, and a tap opens that list. What a past list still has to buy carries over to today's list (made if needed) when Shopping is opened, at
midnight while it is open, and just before today's list reminds you; an item today's list already has isn't added
twice. A past list with nothing left to buy (empty or all ticked) goes to the recycle bin by itself when Shopping is
opened; one with things still to buy stays, and so does the list on screen. A list made for a
past day afterwards, or brought back from the bin, is kept.

## Look and feel

SmartNotes has its own colours, the same on every phone, light or dark (Settings → Appearance): warm paper, with
amber for Notes, teal for Tasks and coral for Shopping. Text is in Nunito. Notes, tasks and items sit on soft cards.
Ticking something springs and gives a small buzz (the phone's touch-feedback setting applies); ticking off the last
task or the last thing to buy sets off a little confetti. Tasks and Shopping show how far along you are ("2 of 5
done"). Each tab has a one-time tip; *Got it* hides it for good (kept on the phone only). Note previews show
checklists as ☐ / ☑ and links by their title, without the #tags (they're listed under the note).

## Where you left off

The app opens where it was left: the same tab, or the note or task that was open (Back goes to its list), with the
Notes search and tag filter and each list's scroll position as they were. Not when it is opened from a reminder or a
share, and Settings and the recycle bin aren't reopened. This is kept on the phone only (not in any backup).

## Sorting and your own order

The sort button (three lines) on Notes, Tasks and Shopping picks the order, remembered (and kept in backups):

| Where | Orders (first is the default) | Kept apart |
|---|---|---|
| Notes | Last edited, Created, Title A–Z, My order | pinned notes stay on top |
| Tasks | Priority, Due date (a date without a time counts as 9:00), Newest, A–Z, My order | done tasks stay below |
| Shopping items | Added, A–Z, My order | ticked items stay below |
| Shopping lists | By date, My order | Anytime, Today and Tomorrow always first; past lists are under *Past*, newest first |

Each row has a handle (≡): drag it to move the row (pinned notes among the pinned). Dropping it makes the order you
see, with that move, *My order*, and the sort switches to it. Choosing My order from the menu the first time starts
from the order then on screen. New notes and tasks go on top, new items and lists at the end. Notes can't be dragged
while searching or filtering by a tag, and done tasks and ticked items stay below. Lists are arranged in Shopping →
sort → *Arrange lists…* (dragging a chip itself would clash with scrolling the row and long-press selecting).

Ticking is only on the box itself: a tap on a shopping item's name, or a note's checklist line, does nothing.

Completed rows are hidden to start with: done tasks fold into a *Done (3)* line and ticked shopping items into *In the
basket (3)*; *Show* / *Hide* on that line (or *Show completed* in the sort menu) switches them, remembered for Tasks
and for Shopping. *Clear done* and *Clear ticked* work either way.

## Selecting, deleting and the recycle bin

Long-press a note, task or shopping list (its chip) to select it; tap more to add them; the top bar then has
*Select all* and *Delete* (✕ or Back stops selecting). Deleting, here or from a note's or task's own screen, moves
things to the **recycle bin** (reminders cancelled) and shows *Undo* for a moment; ✕ on a single shopping item removes
it at once, also with *Undo*. The bin (Notes ⋮ menu, or Settings → Recycle bin) lists deleted notes, tasks and lists
with the days left; tap one to *Restore* or *Delete forever*, long-press to do several, or *Empty bin*. Settings →
Recycle bin → *Keep deleted items for* 7 / 30 (default) / 90 days or Forever; older ones are erased when the app
starts. A restored list whose day has another list by now comes back without a date.

## Backup

Settings → Backup. **Back up now** saves one file (`SmartNotes-backup-<date-time>.json`, readable JSON) wherever you
pick: all notes, tasks and shopping lists (the recycle bin too), shopping suggestions and the settings. Not in it: the
debug log, update state and alarms (set again from the tasks and lists). **Restore from file** checks the file first
(not a backup, damaged, or from a newer SmartNotes: refused), says what it holds and asks, then **replaces everything**
in the app in one go. A copy of what was there is kept first (the last 3), so **Undo last restore** puts it back.
**Automatic backup** Off / Daily / Weekly writes into a folder you choose once (a phone folder or a synced one such as a
cloud drive's) as `SmartNotes-auto-<date-time>.json` and keeps the newest 5 of those (backups made by hand are never
removed); Settings shows the last backup's time or what went wrong. After a restore an open note or task closes, and
the app forgets where it was left (those may be other notes now). Snoozed reminders aren't kept through a restore.

## Reminders

As in Planner: exact alarms (on time when the phone sleeps), set again after a restart, an app update, or a clock or
time-zone change; one whose time passed while the phone was off shows afterwards as *Missed*. A reminder's sound is
*Notification sound only* or rings the alarm sound through silent and vibrate mode for 10 s, 30 s, 1 min or until
stopped (it gives up after 10 minutes, leaving a missed notification). *Default* is Settings → Reminders (Ring 10
seconds to start with). Buttons: **Done** (ticks the task; dismisses a shopping list's), **Snooze**
(10 minutes, 1 hour, tomorrow at 9 am) and, while ringing, **Stop** (a timed ring then leaves its notification; so does
cancelling Snooze). Done on an old notification does nothing once the task has moved on. Alarms are also set again
each time the app starts (a force-stop clears them). Tapping a reminder opens its task or list. Notes have no reminders (until 0.2.0 `@dates` in notes did;
any still set are cancelled).

## Settings

Version; theme (System / Light / Dark); text size (80–160 %, on top of the phone's own font size); date format
(12/11/2026, Wed 12 Nov, 11/12/2026 or 2026-11-12, for shopping lists and tasks); reminder sound
and *Test alarm*; the recycle bin; backup; updates from GitHub; the debug log. Every screen but Notes has a back arrow.

## Updates

The release app (`io.github.baslawson.smartnotes`) checks the latest release of
[baslawson/SmartNotes](https://github.com/baslawson/SmartNotes) at most once a day when it starts (can be switched
off). It also checks when you tap *Check now*. It only offers a new version: with *Download updates automatically*
off (the default) nothing downloads until *Update now*. The APK is checked against the release's SHA-256, and
Android asks before installing. A release needs these two files:

- `SmartNotes.apk`, signed with the same release key every time,
- `SmartNotes.apk.sha256`, holding `sha256sum SmartNotes.apk` output.

The release tag is the version, e.g. `v0.1.0`, matching `versionName` in `app/build.gradle.kts`. Raise `versionCode` too.
The debug build (`.debug`) is a separate app and never updates itself.

## Debug log

`files/logs/app.log` on the phone (plus `app.log.1`, about 1 MB in all). It records start-ups (version, Android
version, device), crashes with stack traces, saves and deletes (with note titles), reminders scheduled, fired or
skipped, task toggles, shopping adds, shares and update checks/downloads. It is never sent anywhere. Settings →
Debug log: *Export* (saves a .txt where you choose), *Copy* (newest ~200 KB to the clipboard), *Show*, *Clear*.

## Build

Needs JDK 17+ and the Android SDK (platform 36). `local.properties` holds `sdk.dir`.

```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM tests: parsing, search, shopping, log file, updates, settings
./gradlew lintDebug
./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
                               # draws the real screens with sample data (Robolectric), light and dark,
                               # to app/build/screenshots/*.png: for looking at a design change, not compared
./gradlew assembleRelease      # signed when keystore.properties exists (git-ignored):
                               # storeFile=…, storePassword=…, keyAlias=…, keyPassword=…
```

Targets Android 16 (API 36, e.g. Samsung Galaxy S26), works from Android 8 (API 26). Edge-to-edge and predictive
back are on.

## Layout

```
app/src/main/java/io/github/baslawson/smartnotes/
  parse/       NoteParser (tags, links, checkboxes), Search, Related, Shopping: plain Kotlin, unit tested
  data/        Room entities and DAOs, Note/Todo/Shopping repositories, RecycleBin, Backups and BackupFormat,
               Updates (GitHub releases)
  reminders/   ReminderRules (sounds, repeat, snooze, alarm planning; unit tested), AlarmScheduler and receivers,
               AlarmService (ringing), SnoozeActivity
  ui/          screens and their ViewModels
  AppLog.kt    the debug log;  AppSettings.kt  settings;  AppState.kt  where the app was left
  MainActivity.kt, SmartNotesApp.kt
app/schemas/   Room schema per database version (for migrations)
```

## Known limits

- Reminders wait for the first unlock after a restart (Planner can ring before it; SmartNotes doesn't yet).
- Samsung's battery management ("Sleeping apps") can still hold an app back: if reminders don't come, set
  SmartNotes to *Unrestricted* under App info → Battery.
- Only text can be shared in (not images or files).
- The notification permission is asked once, on first start (Android 13+).

## Licence

Copyright (C) 2026 baslawson. SmartNotes is free software, licensed under the GNU General Public License,
version 3 only (GPL-3.0-only). The full text is in [LICENSE](LICENSE). It comes with no warranty.

The Nunito font (app/src/main/res/font) is by the Nunito Project Authors, under the SIL Open Font License 1.1
([licenses/Nunito-OFL.txt](licenses/Nunito-OFL.txt)).
