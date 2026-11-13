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
- Light and dark mode, plus a jar app icon.
- Example jars on first launch.
- Unit tests for search, tags and sorting (`SearchTest`).

Data is saved as a single JSON file in the app's private storage, with atomic writes.

## Still to build (in plan order)

1. ~~Pen editor with lasso~~ (done in version 0.2). A possible later upgrade is the Jetpack Ink library for even lower stylus delay.
2. Pictures and files: Photo Picker, camera, **document scanner (#14)**, PDF viewer, sharing into the app, **remove originals from the gallery (#17)**
3. **Writing on pictures and PDFs (#11)**
4. Fonts and **bold, italic, highlight and colour (#2)**
5. Secret jar: PIN or fingerprint, encryption
6. **Reminders and due dates (#27)**
7. **Searching handwriting and photos with ML Kit (#24)**
8. **Google Drive backup (#36)**, then release prep

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
- `ui/note/`: note editor (title, text, tags, checklist, pin)
- `ui/pen/`: pen editor (`PenScreen` handles gestures and zoom, `PenState` handles tools, lasso and undo, `PageRender` draws paper, ink and page previews)
- `data/Ink.kt`: strokes, pages and the lasso, eraser and move/resize math (tested in `InkTest`)
- `ui/theme/`: colours from the prototype
