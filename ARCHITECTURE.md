# Architecture

How Pure PDF is built. Single-module Android app (`:app`), Kotlin + Jetpack Compose + Material 3, no DI framework — app-wide singletons live in a tiny [`AppContainer`](app/src/main/java/com/auskraft/purepdf/PurePdfApplication.kt).

## Package map (`com.auskraft.purepdf`)
```
MainActivity            splash, edge-to-edge, VIEW/SEND intent → reader
PurePdfApplication      AppContainer (DB, repos, RatingManager); registers a launch
data/
  settings/             DataStore: AppSettings + enums (AccentPreset, LibraryView, Density)
  db/                   Room: RecentDocEntity, BookmarkEntity, DAOs, AppDatabase
  LibraryRepository     recents + bookmarks + position; persists SAF grants or local copies
  RatingManager         launch count / prompt / store + feedback email (DataStore)
pdf/
  PdfDocumentController PdfiumCore: open, page sizes, render→Bitmap (LRU, single thread), text
  PdfSearchEngine       whole-doc case-insensitive search over lazy page text
  PdfThumbnailCache     first-page thumbnails + page count for the library (cached, off-thread)
ui/
  App.kt                root: theme, consent gate, floating glass nav, navigation, snackbar, docs/rate overlays
  SettingsViewModel · AppViewModelProvider
  theme/                PurePdfTheme (seed→ColorScheme), LocalPaperColors, Type, Shape
  library/              LibraryScreen (+ DocThumb w/ real previews), LibraryViewModel
  settings/             SettingsScreen (reading, appearance, app)
  reader/               ReaderViewModel, ReaderScreen, PageView, ReaderChrome, ReaderDialogs, ReaderScrollBar
  rate/                 RateSheet (star bottom sheet)
  support/              SupportScreen, SupportPrompt, animated library heart
  docs/                 DocsScreen + ConsentScreen + DocsContent (terms / privacy / data-processing / licenses)
```

## How key features are implemented

**Theme & accent** — [`theme/Theme.kt`](app/src/main/java/com/auskraft/purepdf/ui/theme/Theme.kt). `PurePdfTheme(accent, dark)` calls MaterialKolor's `rememberDynamicColorScheme(seed, isDark)` — the Compose equivalent of the prototype's `makeScheme()`. The 4 accents are seeds on `AccentPreset`. Reader "paper" colors live outside the M3 scheme in `LocalPaperColors` (white/near-black for light; dark-grey/light-grey for dark).

**Persistence** — settings in **DataStore** ([`SettingsRepository`](app/src/main/java/com/auskraft/purepdf/data/settings/SettingsRepository.kt)); recents (with last page+zoom) and bookmarks in **Room** ([`db/`](app/src/main/java/com/auskraft/purepdf/data/db)). `LibraryRepository.recordOpen()` takes a persistable URI grant when the provider offers one; otherwise it immediately imports the PDF into private app storage (`filesDir/imported_pdfs`) while the temporary grant is still alive. This keeps recents working for Telegram/MediaStore/share URIs after restart and offline, while the stable `docKey` remains the original source URI so position/bookmarks survive.

**PDF engine** — [`PdfDocumentController`](app/src/main/java/com/auskraft/purepdf/pdf/PdfDocumentController.kt) opens the doc from a `ParcelFileDescriptor`, serializes all native Pdfium calls onto a single thread, renders pages to ARGB bitmaps (byte-bounded `LruCache`, keyed by page+width), and exposes per-page text + highlight rects. `mapRectToDevice()` converts Pdfium page coordinates to bitmap pixels for highlights. Search ([`PdfSearchEngine`](app/src/main/java/com/auskraft/purepdf/pdf/PdfSearchEngine.kt)) matches against the original page text (`indexOf(..., ignoreCase=true)`) so char indices stay aligned with Pdfium.

**Reader** — [`ReaderScreen.kt`](app/src/main/java/com/auskraft/purepdf/ui/reader/ReaderScreen.kt). A `LazyColumn` of [`PageView`](app/src/main/java/com/auskraft/purepdf/ui/reader/PageView.kt)s renders bitmaps asynchronously. Zoom drives the displayed width live; a debounced `renderScale` (rounded **up** to the next half-step, ≥ display size) re-renders the bitmap so it downscales (sharp) instead of upscaling (stretched). Two zoom modes (`ZoomMode`): **Gesture** — pinch handled at the *Initial* pointer pass (so the list can't double-scroll under two fingers) and anchored at the finger centroid both axes; two-finger horizontal pan; glass zoom buttons hidden. **Buttons** — bottom-right liquid-glass ZoomIn/ZoomOut (Haze, 0.5× steps to 3×): pinch is disabled and one finger pans photo-style in both axes (intercepted before the list, with touch-slop so taps still toggle chrome). Layout subtlety: the oversized page (`requiredWidth` > viewport) is auto-centred by Compose, so `PageView` applies only `panX` on top — adding the centring offset manually double-shifts the page (the original "drifts to a corner" bug). Double-tap toggles 1×↔1.75× (or resets button zoom); single tap hides the chrome (animated bars + floating page indicator). [`ReaderScrollBar`](app/src/main/java/com/auskraft/purepdf/ui/reader/ReaderScrollBar.kt) is a minimap-style indicator at the right edge (the mobile take on VS Code's minimap): a thin thumb mirroring viewport position/size, draggable for fast-scroll with a glass page bubble; it shows while scrolling or while the chrome is visible, fades after ~1.5 s idle, and is skipped for documents under 4 pages. Night mode applies a luminance-inverting `ColorMatrix` to the page image (white→#191919). Current page is derived from `LazyListState`; position is saved debounced.

**Navigation, nav bar & gating** — no nav library; `App.kt` holds a tab + nullable reader/docs overlay state, gated behind a first-launch `ConsentScreen` (DataStore `consentAccepted` flag; settings load as nullable so the splash navy holds instead of flashing the gate). The bottom nav is a **floating frosted-glass pill** (centred so the icons sit close together) over a Haze-blurred backdrop — the tab content is the haze source (`Modifier.haze`), the pill is the haze child (`Modifier.hazeChild`). `BackHandler`s implement predictive back: reader→library (search closes first), docs sub-screen→list→close, Settings tab→Library (`android:enableOnBackInvokedCallback=true`).

**Library previews** — [`PdfThumbnailCache`](app/src/main/java/com/auskraft/purepdf/pdf/PdfThumbnailCache.kt) renders each recent's first page off-thread (LRU-cached) and returns its page count; rows fall back to a stylised placeholder when a document can't be opened (for example, an old pre-import recent whose URI grant is already gone). Page count is backfilled into Room from both the reader and the thumbnail render, and shown as "size · N стр. · date".

**Intents** — `MainActivity` extracts a PDF Uri from `ACTION_VIEW`/`ACTION_SEND` (and `onNewIntent`, `launchMode=singleTask`) into a `StateFlow` the composition consumes and opens directly.

**Rating** — [`RatingManager`](app/src/main/java/com/auskraft/purepdf/data/RatingManager.kt) counts launches in DataStore; `App.kt` auto-prompts once after 3 launches, and Settings has a manual "Оценить приложение" row. [`RateSheet`](app/src/main/java/com/auskraft/purepdf/ui/rate/RateSheet.kt): `hasStore` gates the action — 4–5★ open `STORE_URL` (the RuStore listing), otherwise a thank-you snackbar; 1–3★ open a feedback email.

**Voluntary support** — [`SupportManager`](app/src/main/java/com/auskraft/purepdf/data/SupportManager.kt) owns the payment, terms and author URLs plus a separate DataStore for prompt timing. The library header heart and Settings row open [`SupportScreen`](app/src/main/java/com/auskraft/purepdf/ui/support/SupportScreen.kt), with a direct external-browser payment action and the unchanged source QR under `drawable-nodpi`. After 14 days, `App.kt` may show `SupportPrompt`; it records the actual display time, waits 14 days between displays and gives the rating prompt priority. A successful browser handoff disables future automatic prompts, while manual entry remains available. The app has no payment SDK, Internet permission or payment-result access.

**Documentation & consent** — [`docs/`](app/src/main/java/com/auskraft/purepdf/ui/docs): a "Документация" Settings row opens `DocsScreen` (Terms / Privacy / Data-processing / Licenses, text in `DocsContent.kt`, rendered in a scrollable viewer). `ConsentScreen` reuses the same viewer for the first-launch gate; the legal texts mirror the operator's real documents (adapted to Pure PDF) and are also published at `legal/index.html` for the store's privacy-policy URL.

**Release build** — `app/build.gradle.kts` reads signing from a gitignored `keystore.properties` (debug-key fallback so the minified build stays testable without secrets). Release enables R8 (`isMinifyEnabled` + `isShrinkResources`) with keeps for pdfium JNI and the persisted enums, and restricts ABIs to `arm64-v8a`/`armeabi-v7a` → a ~11 MB universal APK. Full publishing steps in [RUSTORE.md](RUSTORE.md).

## Dependency pins (important)
This project is deliberately on the **late-2024 AGP-8.5 stack**. The versions are interlocked — see `gradle/libs.versions.toml`:
- **pdfiumandroid 1.0.33** (2.0.x needs compileSdk 36 / AGP 8.9+).
- **Kotlin 2.2.0 + KSP 2.2.0-2.0.2** (pdfium 1.0.33 ships Kotlin 2.2.0 metadata); `ksp.useKSP2=false` because Room 2.6.1 predates KSP2.
- **material-kolor 2.0.0** (4.x drags in Compose 1.10 / lifecycle 2.9 → AGP 8.6+).
- **haze 1.0.0** (glass nav/buttons/bubbles): 1.0.0 stays on Compose 1.7.x; haze 1.6+/1.7+ jump to Compose 1.8 → AGP 8.6+. API: `Modifier.haze(state)` + `Modifier.hazeChild(state, shape, style)`.
- AGP 8.5.2, Gradle 8.7, Compose BOM 2024.09.00, compileSdk 35.

Bumping one lib to a 2025 release usually cascades into compileSdk 36 / AGP 8.6+. If you must, bump AGP→8.11 / Gradle→8.14 / compileSdk→36 together.
