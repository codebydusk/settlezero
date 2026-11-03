# SettleZero

> **Split bills, settle to zero. Keep the app free.**  
> A deliberately boring, simple, accountless, local-first expense splitter for groups of people who just want to know **who owes whom**.

---

## 1. Product Philosophy

SettleZero is an open, accountless, lightweight expense-splitting application. It handles the core problem—tracking shared costs and simplifying repayments—without the bloat, subscriptions, advertising, paywalls, or privacy violations common in modern SaaS products.

* **No Accounts:** No email, password, phone number, or social login required. Identity is lightweight and device-scoped.
* **Local-First & Offline-First:** The app reads from and writes to the local SQLite database. Everything works offline: creating groups, adding members, splitting expenses, calculating balances, simplifying debts, and exporting data.
* **Deterministic Ledger:** Balances and simplified repayments are pure, derived projections calculated from immutable expenses and settlements. Balances are never stored as authoritative columns.
* **Integer Minor-Unit Currency:** All amounts are stored as 64-bit integers (`Long`) based on ISO-4217 minor unit scales (paise, cents, fils). Floating-point arithmetic is strictly prohibited.
* **No Paywalls or Subscriptions:** Zero 10-second wait timers. Zero expense limits. 100% free.

---

## 2. Monorepo Structure

The repository is organized as a clean monorepo with distinct domain boundaries:

```text
settlezero/
├── android/              # Native Android application (Jetpack Compose, Room, WorkManager)
│   ├── app/              # Application entrypoint, NavHost, Hilt setup
│   ├── core/             # Shared foundational modules
│   │   ├── common/       # DispatcherProvider, Result monad, DateTime utilities
│   │   ├── database/     # Room DB, Entities, DAOs, TypeConverters, Migrations
│   │   ├── domain/       # Pure Kotlin calculation engine (100% unit-testable on JVM)
│   │   ├── model/        # Pure Kotlin domain models (zero framework dependencies)
│   │   ├── network/      # HTTP client, sync API DTOs, request signers
│   │   ├── sync/         # Jetpack WorkManager workers & sync orchestrator
│   │   └── ui/           # Compose Material 3 theme, design tokens, shared widgets
│   └── feature/          # Self-contained UI features (dashboard, group, expense, settle, claim)
│
├── backend/              # Boring monolith REST API (sync, membership, notifications)
│   ├── cmd/server/       # Server entrypoint
│   ├── internal/         # API routes, auth verification, sync processor, database store
│   └── migrations/       # SQL schema migrations
│
├── web/                  # Landing page & invite link fallback resolver
│   ├── public/           # Static assets, badges, illustrations
│   └── src/              # SettleZero web presence & deep-link redirection
│
├── contracts/            # Shared cross-platform specifications
│   ├── json-schemas/     # SettleZero portable JSON export/import v1.0 schema
│   └── openapi/          # OpenAPI 3.0 specification for the sync API
│
└── docs/                 # Authoritative product specifications & technical architecture
```

---

## 3. Architecture & Key Systems

### Android Client
* **UI Toolkit:** 100% Jetpack Compose with Material 3, supporting Dynamic Color, Dark Mode, and Edge-to-Edge displays.
* **Architecture Pattern:** Clean Architecture with Unidirectional Data Flow (UDF) & MVI state modeling.
* **Persistence:** Room (SQLite) with foreign keys, soft-delete mechanisms, and relational integrity.
* **Background Sync:** Jetpack WorkManager with exponential backoff and network constraints for durable offline queues surviving device reboots and Doze mode.
* **Deterministic Calculations:**
  * **Equal Split Remainder:** Remainder minor units distributed deterministically using lexicographically sorted `Member ID`s.
  * **Debt Simplification:** Greedy min-cash-flow algorithm with strict lexicographical tie-breaking on `Member ID`s to ensure identical repayment graphs across all distributed clients.

### Security & Privacy
* **Hardware Keystore:** Each device generates an asymmetric Ed25519 signing keypair stored in the hardware-backed Android Keystore.
* **SAS Claim Verification:** Existing-member claims use a 4-digit Short Authentication String (SAS code) displayed to the claimant and verified out-of-band by the Admin, preventing unauthorized takeover via shared invite links.
* **Zero Tracking SDKs:** Deferred deep linking operates entirely through the standard Google Play Install Referrer API and system clipboard fallback—no third-party tracking or telemetry SDKs.

---

## 4. Documentation Index

Detailed architectural specifications, user journeys, and consensus reviews are maintained in the [`docs/`](./docs) directory:

1. [**Product Draft**](./docs/1.%20settlezero_product_draft.md) — Initial concept, product philosophy, and core motivations.
2. [**Product Specification v0.1**](./docs/2.%20settlezero_product_specification_v0.1.md) — Authoritative v0.1 product rules, permissions, and scoping.
3. **User Journeys:**
   * [**Journey 1: First Launch & Group Creation**](./docs/3.1%20settlezero_user_journey_1_v0.1.md)
   * [**Journey 2: Invite Links & Membership Claims**](./docs/3.2%20settlezero_user_journey_2_v0.1.md)
   * [**Journey 3: Expenses, Splits & Audit History**](./docs/3.3%20settlezero_user_journey_3_v0.1.md)
   * [**Journey 4: Balances, Simplification & Settlements**](./docs/3.4%20settlezero_user_journey_4_v0.1.md)
   * [**Journey 5: Admin & Member Lifecycles**](./docs/3.5%20settlezero_user_journey_5_v0.1.md)
   * [**Journey 6: Offline Engine, Sync & Conflicts**](./docs/3.6%20settlezero_user_journey_6_v0.1.md)
4. [**Cross-Journey Consistency Review**](./docs/4%20settlezero_cross-journey_consistency_review_v0.1.md) — Resolution of cross-document edge cases and final consensus rules.
5. [**Technical Architecture Specification v0.1**](./docs/5.%20settlezero_technical_architecture_v0.1.md) — Complete engineering blueprint, Room schema DDL, algorithms, and sync protocols.

---

## 5. Developer Setup

### Prerequisites
* **JDK:** OpenJDK 17 or 21
* **Android Studio:** Android Studio Ladybug (2024.2.1+) or newer
* **Android SDK:** API Level 35 (compileSdk), minimum API Level 26 (minSdk)
* **Package/Version Manager:** [mise](https://mise.jdx.dev/) (optional, for Node/Go tooling)

### Android Project Setup
1. Open the **`android/`** subfolder directly in Android Studio:
   ```bash
   # Or launch directly from terminal
   studio ./android
   ```
2. Build the project via Gradle:
   ```powershell
   cd android
   ./gradlew assembleDebug
   ```
3. Run the pure Kotlin domain calculation tests (executes in milliseconds without emulators):
   ```powershell
   cd android
   ./gradlew :core:domain:testDebugUnitTest
   ```

### Backend Setup (Upcoming)
```powershell
cd backend
# Instructions will be added during backend scaffolding
```

### Web Setup (Upcoming)
```powershell
cd web
npm install
npm run dev
```

---

## 6. License

This project is licensed under the terms specified in the [`LICENSE`](./LICENSE) file.
