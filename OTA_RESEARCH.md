# OTA and local-first research note

Date: 2026-09-25

## Distribution decision

The app has no repository license file. No third-party source code was copied. The implementations in this iteration are independent and use only public Android APIs and the official Play libraries.

- Google Play distribution: `com.google.android.play:app-update:2.1.0` and `app-update-ktx:2.1.0`.
- Sideload distribution: the app only reads a small HTTPS manifest and validates package name, higher version code, artifact size/hash, and the installed signing-certificate hash. It does not download or install arbitrary APKs.
- An empty or missing sideload channel is a safe unavailable state, not an invitation to load an untrusted file.
- A future sideload installer must first download to private storage, verify the artifact SHA-256, inspect the archive package and signer, show explicit user consent, and use a recoverable test channel. Automatic rollback is not implemented because it is not a safe general Android user flow.

Official references:

- https://developer.android.com/guide/playcore/in-app-updates
- https://developer.android.com/guide/playcore/in-app-updates/kotlin-java
- https://developer.android.com/google/play/app-updates
- https://developer.android.com/reference/android/widget/RemoteViews

## Ideas reviewed and license status

Only ideas were considered; implementation code was not copied.

| Reference | License found during review | Useful idea | Decision |
|---|---|---|---|
| https://github.com/willbsp/habits | GPL-3.0-or-later; archived in 2026 | Flexible repeat intervals | Keep as a later domain-model change; do not copy code without project license review |
| https://github.com/android/trackr | Apache-2.0 | Accessibility, offline-first UI, local data ownership | Apply only general accessibility patterns in native code; do not import sample code into this project |
| https://github.com/dessalines/habit-maker | AGPL-3.0 | Progress/score instead of guilt and descriptive feedback | Consider a neutral completion score later; do not copy code into a project without compatible licensing |
| https://github.com/Jinjinov/OpenHabitTracker | GPL-3.0 | Overdue measurement, import/export, repeat rules | Import/export and overdue score are high-value follow-ups, but require schema/version design and are not being smuggled into this iteration |
| https://github.com/DanielRendox/RoutineTracker | GPL-3.0 | Offline-first planning and flexible scheduling | Same licensing caution; use only independently specified product ideas |

## Implemented small improvements

- Play updates use availability checks, priority and staleness policy, flexible/immediate flows, downloaded-state completion, and clear decline/error states.
- Sideload metadata validation has refusal tests for wrong package, same/downgrade version, HTTP, malformed hash, and wrong signer.
- Widget card clicks use per-instance unique URI data, so PendingIntents from separate widget instances cannot overwrite each other.
- Widget card updates run on resize and restore their habit binding after launcher backup restoration.
- Widget RemoteViews layouts contain only supported layout/view classes.

## Deferred safely

Flexible repeat intervals, import/export, overdue scoring, durable offline voice transcription, and notifications need separate migrations and visual tests. They should not be mixed into the widget/OTA fix without a stable Room migration and a tested recovery path.
