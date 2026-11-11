# SmartNotes

An offline Android notes app (Kotlin, Jetpack Compose, Room). Everything "smart" is read from the note's plain
text, so the text stays the single source of truth:

| Write | You get |
|---|---|
| `#groceries` | a tag (filter by it on the Notes screen) |
| `[[Trip plan]]` or `[[Trip plan\|shown]]` | a link to the note with that title; the other note lists it under *Linked from* |
| `- [ ] buy milk` / `- [x] done` | a task, collected on the Tasks screen (tick it there or in the note) |
| `@2026-11-20 14:00`, `@2026-11-20` (09:00) | a reminder notification |
| `@today`, `@tomorrow`, `@fri`, `@friday` | turned into a date when you leave the note (on that weekday itself, it means today) |

Also:
- **Search**: full-text across all notes (Room FTS4), word prefixes, matches highlighted.
- **Related notes**: shown under each note, from shared tags and words.
- **Shopping**: its own list. Type `2x milk`, `milk 2L` or `eggs x12`: the quantity is shown apart from the
  name. Past items are suggested while typing (tap ✕ on a suggestion to forget it). Adding an item already
  on the list doesn't make a second one. Open tasks in notes tagged `#shopping` (or with `#shopping` on the task line)
  show under *From notes*.
- **Share into SmartNotes**: text or a link shared from another app becomes a new note (the subject is its title).
- **Settings**: version; theme (System / Light / Dark); text size (80–160 %, on top of the phone's own font size);
  updates from GitHub; the debug log.

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
./gradlew assembleRelease      # signed when keystore.properties exists (git-ignored):
                               # storeFile=…, storePassword=…, keyAlias=…, keyPassword=…
```

Targets Android 16 (API 36, e.g. Samsung Galaxy S26), works from Android 8 (API 26). Edge-to-edge and predictive
back are on.

## Layout

```
app/src/main/java/io/github/baslawson/smartnotes/
  parse/       NoteParser (tags, links, tasks, reminders, dates), Search, Related, Shopping: plain Kotlin, unit tested
  data/        Room entities and DAOs, NoteRepository, ShoppingRepository, Updates (GitHub releases)
  reminders/   WorkManager scheduling, the reminder worker, notifications
  ui/          screens and their ViewModels
  AppLog.kt    the debug log;  AppSettings.kt  theme and text size
  MainActivity.kt, SmartNotesApp.kt
app/schemas/   Room schema per database version (for migrations)
```

## Known limits

- Reminders use WorkManager, so Android may deliver them a few minutes late when the phone is idle. Samsung's
  battery management ("Sleeping apps") can delay them further: set SmartNotes to *Unrestricted* under
  App info → Battery if reminders arrive late.
- Only text can be shared in (not images or files).
- The notification permission is asked once, on first start (Android 13+).
