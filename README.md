# Vault

A local-first Android personal finance tracker focused on making everyday money movements easier to review and understand.

Vault combines transaction history, account tracking and spending summaries with optional SMS-assisted capture. Users review detected details before transactions enter their ledger.

## Project status

Active development prototype. The core flows are implemented; broader financial edge-case testing, SMS-format validation and physical-device testing remain in progress. This repository does not represent a production-ready or Google Play-approved SMS release.

## Features

- Manual transaction entry, editing and deletion with a short Undo window.
- Custom income/expense categories, archiving and confirmed permanent deletion.
- Search, date/account/category filters, monthly summaries and category drill-down.
- Optional bank, cash, wallet and credit-card accounts.
- Balanced ledger entries for transfers, cash withdrawals, linked refunds and reversals.
- Generic CSV import with column mapping, preview and duplicate warnings; filtered CSV export.
- Local onboarding with optional account setup and separate SMS/notification choices.
- **Debug build:** optional incoming financial SMS parsing, correctable review, and notifications that open a compact review form.

Historical SMS scanning is implemented but deliberately disabled. PDF statement parsing, voice input, automatic categorization rules, family sharing and bill splitting are future work.

## Privacy and data handling

Financial records are stored on-device in Room. Current SMS processing and routine calculations run locally; there is no backend, cloud sync or required signup.

SMS access is optional. Relevant raw message text is temporarily staged, cleared after processing, or expired on a later cleanup after seven days. Parsed details remain locally for review. The expiry is best effort while the app can run. OS backup/device transfer exclusions are configured; encrypted backup and application-level database encryption are not implemented.

The release manifest omits SMS permissions and the SMS receiver. SMS capture and transaction-review notification entry points are debug-only. Notification permission is requested separately, and Android/user settings control whether notification banners appear.

## Technology

- Kotlin and Jetpack Compose / Material 3
- ViewModels, StateFlow and Kotlin coroutines
- Room / SQLite with explicit migrations and exported schemas
- Hilt dependency injection
- WorkManager for durable SMS processing and recovery
- Preferences DataStore for local settings and onboarding
- Navigation Compose and Android's Storage Access Framework

The application uses one Gradle app module with `data`, `domain` and `presentation` packages. Screens use MVVM; repositories and use cases keep financial and persistence logic outside composables. Ledger writes and SMS source claims use a shared transactional path.

## Repository layout

```text
vault_android/
├── app/
│   ├── schemas/          # Exported Room schemas
│   └── src/
│       ├── main/         # Application code and resources
│       ├── debug/        # SMS manifest and development seeding
│       ├── release/      # Release-specific implementations
│       ├── test/         # JVM tests
│       └── androidTest/  # Device/database integration tests
└── gradle/
    └── libs.versions.toml
```

Personal planning documents and standalone UI prototypes are kept outside the published application tree.

## Run locally

1. Clone this repository and open **`vault_android`** in Android Studio.
2. Use the JDK required by the pinned Android Gradle Plugin (the project compiles Java/Kotlin for Java 17; Android Studio's compatible bundled JDK is recommended).
3. Install **Android SDK Platform 37**, as specified by `compileSdk`, and let Gradle sync.
4. Select an emulator or device running **Android 7.0 / API 24 or newer**.
5. Build/run the `debug` variant.

From the repository root:

```bash
cd vault_android
./gradlew assembleDebug
```

Debug APK: `vault_android/app/build/outputs/apk/debug/app-debug.apk`.

Debug uses the application ID `com.piyush.vault.debug` and includes development seeding. Release uses `com.piyush.vault` and does not seed demonstration transactions. Use synthetic messages for initial development and deliberately sanitized examples for reporting parser problems.

### Try incoming SMS review

Complete or skip onboarding, then open **More → SMS activity**. Enable incoming capture and optionally enable **Notify me about new transactions**. Grant each permission when prompted. A new supported live alert becomes a review candidate; notifications never save financial transactions automatically. Old messages are not scanned.

## Validation

Existing tests cover selected ledger, CSV, parser, migration and SMS-pipeline behaviours. Coverage is not exhaustive; the newest flows still need device validation.

Run checks explicitly when needed:

```bash
cd vault_android
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

The connected checks require a running emulator/device. A successful build or installation alone does not establish parser accuracy, migration correctness or OEM background-delivery reliability.
