# Architecture

How Pure PDF is built. Single-module Android app (`:app`), Kotlin + Jetpack Compose + Material 3, no DI framework — app-wide singletons live in a tiny [`AppContainer`](app/src/main/java/com/auskraft/purepdf/PurePdfApplication.kt).

## Package map (`com.auskraft.purepdf`)
```
MainActivity            splash, edge-to-edge, VIEW/SEND intent → reader
PurePdfApplication      AppContainer (DB, repos, RatingManager); registers a launch
data/
  settings/             DataStore: AppSettings + enums (AccentPreset, LibraryView, Density)
  db/                   Room: RecentDocEntity, BookmarkEntity, DAOs, AppDatabase
  LibraryRepository     recents + bookmarks + position; resolves names, persists SAF grants
  RatingManager         launch count / prompt / store + feedback email (DataStore)
pdf/
  PdfDocumentController PdfiumCore: open, page sizes, render→Bitmap (LRU, single thread), text
  PdfSearchEngine       whole-doc case-insensitive search over lazy page text
ui/
  App.kt                root: theme, bottom nav, navigation state, snackbar, docs/rate overlays
  SettingsViewModel · AppViewModelProvider
  theme/                PurePdfTheme (seed→ColorScheme), LocalPaperColors, Type, Shape
  library/              LibraryScreen (+ DocThumb), LibraryViewModel
  settings/             SettingsScreen (reading, appearance, app)
  reader/               ReaderViewModel, ReaderScreen, PageView, ReaderChrome, ReaderDialogs
  rate/                 RateSheet (star bottom sheet)
  docs/                 DocsScreen + DocsContent (privacy / terms / licenses)
```

## How key features are implemented

**Theme & accent** — [`theme/Theme.kt`](app/src/main/java/com/auskraft/purepdf/ui/theme/Theme.kt). `PurePdfTheme(accent, dark)` calls MaterialKolor's `rememberDynamicColorScheme(seed, isDark)` — the Compose equivalent of the prototype's `makeScheme()`. The 4 accents are seeds on `AccentPreset`. Reader "paper" colors live outside the M3 scheme in `LocalPaperColors` (white/near-black for light; dark-grey/light-grey for dark).

**Persistence** — settings in **DataStore** ([`SettingsRepository`](app/src/main/java/com/auskraft/purepdf/data/settings/SettingsRepository.kt)); recents (with last page+zoom) and bookmarks in **Room** ([`db/`](app/src/main/java/com/auskraft/purepdf/data/db)). `LibraryRepository.recordOpen()` takes a persistable URI grant so recents reopen after restart and resolves display name/size via `ContentResolver`.

**PDF engine** — [`PdfDocumentController`](app/src/main/java/com/auskraft/purepdf/pdf/PdfDocumentController.kt) opens the doc from a `ParcelFileDescriptor`, serializes all native Pdfium calls onto a single thread, renders pages to ARGB bitmaps (byte-bounded `LruCache`, keyed by page+width), and exposes per-page text + highlight rects. `mapRectToDevice()` converts Pdfium page coordinates to bitmap pixels for highlights. Search ([`PdfSearchEngine`](app/src/main/java/com/auskraft/purepdf/pdf/PdfSearchEngine.kt)) matches against the original page text (`indexOf(..., ignoreCase=true)`) so char indices stay aligned with Pdfium.

**Reader** — [`ReaderScreen.kt`](app/src/main/java/com/auskraft/purepdf/ui/reader/ReaderScreen.kt). A `LazyColumn` of [`PageView`](app/src/main/java/com/auskraft/purepdf/ui/reader/PageView.kt)s renders bitmaps asynchronously. Vertical scroll/fling is native; a custom pointer handler intercepts **two-finger** gestures only (pinch zoom + horizontal pan), leaving one-finger scroll to the list. Zoom drives the displayed width live; a debounced `renderScale` (rounded **up** to the next half-step, ≥ display size) re-renders the bitmap so it downscales (sharp) instead of upscaling (stretched). Double-tap toggles 1×↔1.75×; single tap hides the chrome (animated bars + floating page indicator). Night mode applies a luminance-inverting `ColorMatrix` to the page image (white→#191919). Current page is derived from `LazyListState`; position is saved debounced.

**Navigation & back** — no nav library; `App.kt` holds a tab + nullable reader/docs overlay state. `BackHandler`s implement predictive back: reader→library (search closes first), docs sub-screen→list→close, Settings tab→Library (`android:enableOnBackInvokedCallback=true`).

**Intents** — `MainActivity` extracts a PDF Uri from `ACTION_VIEW`/`ACTION_SEND` (and `onNewIntent`, `launchMode=singleTask`) into a `StateFlow` the composition consumes and opens directly.

**Rating** — [`RatingManager`](app/src/main/java/com/auskraft/purepdf/data/RatingManager.kt) counts launches in DataStore; `App.kt` auto-prompts once after 3 launches, and Settings has a manual "Оценить приложение" row. [`RateSheet`](app/src/main/java/com/auskraft/purepdf/ui/rate/RateSheet.kt): 4–5★ opens the store (`market://` → web fallback), 1–3★ opens a feedback email. `STORE_URL`/`FEEDBACK_EMAIL` are **placeholders** to replace before publishing.

**Documentation** — [`docs/`](app/src/main/java/com/auskraft/purepdf/ui/docs): a "Документация" Settings row opens `DocsScreen`, listing Privacy / Terms / Licenses (text in `DocsContent.kt`) rendered in a scrollable viewer.

## Dependency pins (important)
This project is deliberately on the **late-2024 AGP-8.5 stack**. The versions are interlocked — see `gradle/libs.versions.toml`:
- **pdfiumandroid 1.0.33** (2.0.x needs compileSdk 36 / AGP 8.9+).
- **Kotlin 2.2.0 + KSP 2.2.0-2.0.2** (pdfium 1.0.33 ships Kotlin 2.2.0 metadata); `ksp.useKSP2=false` because Room 2.6.1 predates KSP2.
- **material-kolor 2.0.0** (4.x drags in Compose 1.10 / lifecycle 2.9 → AGP 8.6+).
- AGP 8.5.2, Gradle 8.7, Compose BOM 2024.09.00, compileSdk 35.

Bumping one lib to a 2025 release usually cascades into compileSdk 36 / AGP 8.6+. If you must, bump AGP→8.11 / Gradle→8.14 / compileSdk→36 together.
