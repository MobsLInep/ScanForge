# Play Console Data Safety — ScanForge answers

Use this when filling in the Play Console **Data safety** form. It reflects the shipped build, where
all processing is on-device and crash/analytics reporters are no-op and off by default.

## Summary answers

- **Does your app collect or share any of the required user data types?** — **No** (in the shipped
  build). The app functions entirely on-device. _If you later enable a real crash/analytics backend
  behind the opt-in flags, update this to "Yes" for the Crash logs / App-info-and-performance types
  and complete the sections below accordingly._
- **Is all of the user data encrypted in transit?** — Not applicable (no data is transmitted by the
  shipped build). When export/share/sync is used, transfer security is handled by the destination.
- **Do you provide a way for users to request that their data be deleted?** — **Yes** — data lives on
  the device; users delete documents in-app (Trash → empty) or by uninstalling.

## Data types

| Data type | Collected | Shared | Purpose | Optional |
|---|---|---|---|---|
| Photos / documents (scans, pages) | No (stays on device) | No | App functionality (on-device) | — |
| Files & docs (recognized text) | No (stays on device) | No | App functionality (on-device) | — |
| Crash logs | No in shipped build / Optional if backend enabled | No | Diagnostics | Yes (opt-in, off by default) |
| App interactions (anonymous usage) | No in shipped build / Optional if backend enabled | No | Analytics | Yes (opt-in, off by default) |
| Personal info, contacts, location, identifiers | No | No | — | — |

## Permissions declared

- `CAMERA` — capture pages while scanning (feature optional; import works without it).
- No storage permission — uses the Storage Access Framework for import/export.
- `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` — used by the export
  worker to show progress; no data collection.

## Notes for the reviewer

- On-device ML Kit text-recognition models are bundled (offline). OCR text is not transmitted.
- Crash reporting and analytics are interface seams with **no-op implementations** in this build;
  both are gated behind explicit, off-by-default opt-in toggles in Settings → Privacy.
