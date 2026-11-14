# Paper Star (Android)

A notes app where each subject is a glass jar and every note is a paper star inside it.
The full plan is in [`../ANDROID_PLAN.md`](../ANDROID_PLAN.md). The clickable web prototype is [`../paper-star.html`](../paper-star.html).

## What works so far (version 0.1)

- **Jars on the home screen:** one pastel paper star per note.
- **Star drop:** making a note drops a new star into its jar. It falls, bounces, and the jar jiggles.
- **Shake a jar (#31):** long-press it. It shakes with a vibration and pops out a **lucky star (#33)**, a random note from that jar. The star button at the top pulls a lucky star from any jar.
- **Search:** searches titles, typed text, checklist items, tags (`#exam`) and jar names, with matching words highlighted.
- **Jars:** create, rename, recolour and delete. Each jar has a tag filter row.
- **Notes:** title and typed text with autosave, **checklists (#1)**, **pin to top (#4)**, **tags (#18)**, and delete.
- **Pen editor (version 0.2):** tap **Pen note** in a jar, or **Write with pen** in any note.
  - Tools: pen (line width follows stylus pressure), highlighter (drawn under the ink), and an eraser that removes whole strokes.
  - 4 ink colors, 4 highlighter colors and 3 sizes. Lined, grid or blank paper.
  - Several pages per note, with add and delete. Undo and redo.
  - Pinch with two fingers to zoom and pan (up to 500%), and a "Fit page" button to zoom back out.
  - **Palm rejection:** once a stylus touches the page, fingers only move and zoom, and never draw.
  - **Lasso (#8):** draw a loop around strokes to select them. Then drag to move, drag the corner handle to resize, tap a color to recolor, or duplicate or delete.
  - Notes show small previews of their pages.
- **Pictures, files and scans (version 0.3):** every note has **Pictures**, **Camera**, **Scan** and **File** buttons.
  - Pictures are copied into the app and shrunk to at most 2000 px. They open full screen, with pinch to zoom and double-tap to zoom.
  - **Remove originals from the gallery (#17):** after you add pictures, the app offers to delete the originals, and Android asks you to confirm. Needs Android 11 or newer.
  - **Document scanner (#14):** uses Google's ML Kit scanner, which auto-crops and straightens. One page becomes a picture, and several pages become one PDF. Needs Google Play services, and it downloads a small part the first time.
  - **Files:** attach any file. PDFs open inside the app, with tap-a-page to zoom. Word, PowerPoint and other files open in an app on the phone ("Open in…").
  - **Download:** tap the download arrow on a file, or in the PDF or picture viewer, to save a copy to the phone's **Downloads › Paper Star** folder. On Android 9 and older, Android's save screen opens instead.
  - **Share into Paper Star:** tap Share in another app (Messenger, Files, Gallery…), choose Paper Star, and pick a jar and a new or existing note. Shared text goes into the typed part.
  - File names are searchable. Deleting a note or jar also deletes its files.
- **Bold, italic, underline, highlight and text colour (#2, version 0.4):** a format bar appears above the keyboard while typing.
  - Select words and tap **B**, **I** or **U**, the highlighter (yellow, pink, green or blue) or **A** for text colour (red, blue, green, purple or orange).
  - With nothing selected, a button sets the style for the next letters typed. Typing right after a styled word continues its style, like in Google Docs.
  - Highlights keep dark text in dark mode, and text colours switch to lighter shades. Styles are saved as ranges next to the plain text, so search and old notes are unchanged.
- **Fonts (version 0.5):** tap the **Aa** chip above the typed text, or **Aa** on the format bar. Pick one of 8 bundled Google Fonts (DM Sans, Nunito, Lora, Caveat, Patrick Hand, Bricolage Grotesque, Courier Prime, Comfortaa) or Default, plus Small, Medium or Large. Each name is shown in its own font. The choice is saved per note and shows on note cards. Fonts work offline; their licenses are in [`licenses/FONTS-OFL.txt`](licenses/FONTS-OFL.txt).
- **Writing on pictures and PDFs (#11, version 0.6):** open a picture or PDF in a note and tap **Write on it**.
  - A picture becomes a handwritten page with the picture behind it. The page takes the picture's shape, and the original picture stays as it was.
  - A PDF adds every page (up to 40) as pages to write on. Zoom into one page and tap **Write on page** to add only that page.
  - All pen tools work on top, including the highlighter and lasso. The pages show in the note's Handwritten section like any other page.
- **Searching handwriting and photos (#24, version 0.7):** search also finds words in handwritten pages, pictures, scans and PDFs.
  - Uses Google's ML Kit text recognition on the phone. Nothing is uploaded, and it works offline with no download.
  - It reads things in the background a couple of seconds after you stop editing, and reads a page again only after it changes. PDFs are read up to 20 pages.
  - Results say where the word was found, like "Handwritten page: …" or "Picture: …". Neat print reads best; messy cursive may not be found.
  - The Secret jar is never read or searched.
- **Reminders (#27, version 0.8):** tap **Remind me** on a note. Pick tonight at 8, tomorrow at 7 AM, Saturday at 9 AM, or any date and time.
  - At that time a notification shows the note's title and first line. Tapping it opens the note.
  - The note's chip and its card show when the reminder is due, then "Reminded …" afterwards. Tap the × to remove it.
  - Reminders come back after the phone restarts. Android asks once for permission to show notifications.
  - Notes in the Secret jar only say "A note in your Secret jar", so nothing shows on the lock screen.
  - Android may deliver a reminder up to about a minute late, unless "Alarms & reminders" is allowed for Paper Star in Settings.
- **Backup to Google Drive (#36, version 0.9):** tap the cloud button on Home.
  - **Back up to Google Drive** opens Android's save screen. Choose Google Drive from the ☰ menu and tap Save. After that, **Back up now** updates the same file.
  - **Back up automatically** (on by default) updates the backup once a day when you leave the app.
  - The backup is one zip with every jar, note, picture, file and pen page. **Restore from a backup…** shows how many notes it has and asks before replacing everything.
  - It uses Android's file picker and Drive app, so it needs no Google sign-in or cloud setup in Paper Star. The backup isn't password-protected, and Secret jar notes are included.
  - Android's own device backup is also on (`allowBackup`), as a second safety net.
- Light and dark mode, plus a jar app icon.
- Example jars on first launch.
- Unit tests for search, tags and sorting (`SearchTest`), ink (`InkTest`), files (`FilesTest`) and formatting (`RichTest`).

Data is saved as a single JSON file in the app's private storage, with atomic writes.

## Still to build (in plan order)

1. ~~Pen editor with lasso~~ (done in version 0.2). A possible later upgrade is the Jetpack Ink library for even lower stylus delay.
2. ~~Pictures, files, scanner, gallery cleanup, sharing in~~ (done in version 0.3). Still to do: a jar-wide Pictures tab.
3. ~~Writing on pictures and PDFs (#11)~~ (done in version 0.6)
4. ~~Fonts and bold, italic, highlight and colour (#2)~~ (done in versions 0.4 and 0.5)
5. Secret jar: PIN or fingerprint, encryption
6. ~~Reminders (#27)~~ (done in version 0.8)
7. ~~Searching handwriting and photos with ML Kit (#24)~~ (done in version 0.7)
8. ~~Google Drive backup (#36)~~ (done in version 0.9). Next: release prep

The storage may move to Room once search needs to scale and the Secret jar needs encryption.

## Build and run

You need Android Studio, which is already installed on this PC. Then:

```bash
./gradlew assembleDebug        # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # runs the unit tests
./gradlew installDebug         # installs on a connected phone or running emulator
```

To use your phone: turn on **Developer options → USB debugging**, plug it in, and run `installDebug`.
You can also open this `PaperStar` folder in Android Studio and press Run.

## Code map

- `data/`: models (`Folder` = jar, `Note`, `CheckItem`), `Repository` (load, save, edits, star drops), `Search`, `Seed` examples
- `ui/home/`: jars grid, `JarArt` (drawing, drop and shake animations), search, lucky star, new jar dialog
- `ui/jar/`: one jar's notes, tag filter, rename, recolour and delete
- `ui/note/`: note editor (title, text, tags, checklist, pin); `RichText.kt` has the format bar and styled text
- `ocr/Recognizer.kt`: reads text in pages, pictures and PDFs in the background; `data/Recognized.kt` decides what needs reading (tested in `RecognizedTest`)
- `reminders/Reminders.kt`: alarms, the notification and restoring after a restart; `data/ReminderTimes.kt` has the labels and quick choices (tested in `ReminderTest`); `ui/note/ReminderUi.kt` has the chip and pickers
- `backup/Backup.kt`: backing up, automatic daily backups and restoring; `data/BackupZip.kt` is the zip format (tested in `BackupTest`); `ui/backup/BackupScreen.kt` is the screen
- `data/Rich.kt`: formatting ranges and how they move while typing (tested in `RichTest`)
- `ui/media/`: saved-picture loading, picture viewer, PDF viewer, file rows, "Open in…"
- `ui/note/Attachments.kt`: Pictures, Camera, Scan and File buttons, plus the gallery clean-up prompt
- `ui/share/`: "Save to Paper Star" from other apps
- `data/Imports.kt`: copies pictures and files into app storage
- `ui/pen/`: pen editor (`PenScreen` handles gestures and zoom, `PenState` handles tools, lasso and undo, `PageRender` draws paper, ink and page previews)
- `data/Ink.kt`: strokes, pages and the lasso, eraser and move/resize math (tested in `InkTest`)
- `ui/theme/`: colours from the prototype; `Fonts.kt` has the note fonts and sizes
- `ui/note/FontPicker.kt`: the font chip and picker
