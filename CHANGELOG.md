# Changelog

## 1.0 — 2026
First release. A minimal, ad-free, fully-offline PDF reader, prepared for RuStore.

- **Library** — recents (list / grid, density), **real first-page previews**, size · page count · date, empty state, SAF "Открыть файл".
- **Reader** — PdfiumAndroid rendering, vertical scroll, rotation; two zoom modes (focal pinch + glass buttons with photo-style one-finger pan); double-tap; page-jump dialog, slider, progress.
- **Minimap scroll indicator** — draggable right-edge thumb with a glass page bubble.
- **Liquid-glass nav bar** — floating, blurred, translucent (Haze).
- **Position memory**; whole-document **search** with highlights + next/prev; **bookmarks**.
- **Night mode** — dark scheme + smart-inverted "paper", persisted. **Appearance** — 4 accent presets (Material 3 from seed), library view, density.
- **First-launch consent** screen; in-app **documentation** (terms / privacy / data-processing / licenses); in-app **rating** (RuStore link for 4–5★, feedback email for 1–3★).
- **Privacy** — no INTERNET permission, no analytics, no ads, fully offline.
- **Intents** — open via VIEW / SEND; registers as a PDF viewer.
- **Release** — R8 minify + resource shrinking + arm-only ABIs → ~11 MB signed APK.
