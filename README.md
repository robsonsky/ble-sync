## Hailie BLE Sync SDK — README

A production‑grade BLE Sync SDK for Android (Kotlin), with a demo app (“Scenario Lab”) that runs fully offline via fault‑injectable fakes. The SDK focuses on three persistent problems: pairing reliability, connection stability, and sync performance. Core design pillars: single‑threaded actor runtime, durable commands, explicit state, circuit breakers, backpressure, structured telemetry, and exactly‑once resume.

### Modules

- core-domain: Pure domain logic (commands, events, saga decisions, policies). KMP-friendly.
- ports: Interfaces/contracts for BLE, Delivery, Telemetry, Clock, StateStore.
- core-runtime (actor): Single-threaded orchestrator (timeouts, retries, backoff, breakers, backpressure, resume).
- adapters-android: Android BLE adapter, state store, and telemetry implementations.
- fakes: Fault-injectable fakes (FakeBlePort, etc.) for tests and demo.
- app-demo: Scenario Lab UI (Jetpack Compose). No hardware required; simulates faults and timing.

### Architecture (1‑paragraph)

All BLE work is serialized in a single-threaded actor to avoid concurrent GATT hazards. The runtime consumes durable commands and emits domain events, applying policies for timeouts, retries with decorrelated jitter backoff, and circuit breakers per stage (bond/connect/read/ack). Sync is a backpressure pipeline (fetch → deliver → ack) with an exactly-once high‑water mark (lastAckedExclusive) persisted to storage for crash‑safe resume. Errors are mapped into a strict taxonomy (Security/User, Transport/Radio, Unavailable/Platform, Protocol, Unexpected) to drive decisions (retry, breaker, user prompt, or abort). Structured telemetry is emitted for every stage to make the system observable and tunable.

### How we handle state, errors, and retries

- State
  - Public state via StateFlow: PermissionRequired, UserActionRequired, Connecting, Syncing(progress), Paused(breaker open), Idle/Complete.
  - Snapshot persistence: lastAckedExclusive, breaker windows, and minimal cursor saved to StateStore (EncryptedSharedPreferences on Android).
  - Resume: After crash/restart or reconnect, reading resumes from lastAckedExclusive to ensure no duplicates and no loss.

- Errors
  - Security/User: missing BLUETOOTH_CONNECT, user-denied bonding. Surface as PermissionRequired/UserActionRequired.
  - Transport/Radio: timeouts, disconnections, transient GATT codes (8/19), RF loss. Generally retriable with backoff.
  - Unavailable/Platform: GATT 133, adapter off/busy. Typically retriable and breaker‑gated.
  - Protocol: missing characteristic, malformed/short payload. Non‑retriable until configuration corrected.
  - Unexpected: Catch‑all; captured by telemetry with context for debugging.

- Retries & Breakers
  - Pairing (bond): bounded retries with jitter; breaker opens on repeated failures; requires explicit user path where OS dialogs are involved.
  - Connection (connectGatt): exponential backoff with jitter; breaker opens on repeated failures; reconnect as a command; resume continues from snapshot.
  - Read/Ack: short bounded retries; escalate to reconnect if repeated read failures; ack is idempotent and safe to retry.
  - Circuit Breakers: Closed → Open(cooldown) → Half‑Open(probe) → Closed; metrics emitted for open durations and failures‑before‑breaker.

### Requirements

- Android Studio (Giraffe+ recommended), Gradle 8+, Kotlin 1.9+
- JDK: Use consistent toolchains (17).
- Min SDK: 24 (default), Target SDK: 34
- No BLE hardware required to run the demo

### Build and Run

Android Studio (recommended)
1. Open the project in Android Studio.
2. Select the app module:
   - app-demo (Scenario Lab)
3. Run ▶ to your device/emulator.

Device prep
- Enable Developer Options and USB debugging on your device.

### Scenario Lab (Demo App)

- Presets: Happy Path, Pairing Reliability, Connection Stability, Slow Sync Performance
- Panels:
  - Fault Injection (preset‑driven), Live State/Timeline & Progress, Metrics/Telemetry, Log Viewer
- Controls: Start, Stop, Kill App Now (crash simulation), Cold Start Resume (toggle)
- Export: Copy Logs, Export Telemetry JSON (last session)
- No hardware required. All behavior comes from fakes and fault scripts.

Usage
1. Launch Scenario Lab on your device.
2. Pick a preset (chips at the top).
3. Press Start. Observe:
   - Progress in the center
   - Metrics on the right (pairing time, reconnect latency, pages/min, bytes/min, avg page latency, delivered pages, acked up to)
   - Logs at the bottom (toggle visible)
4. Press Kill App Now to crash intentionally; relaunch to verify snapshot‑based resume (enable Cold Start Resume). For real persistence across process death, wire the Android state store adapter (EncryptedSharedPreferences).

### Code quality

- Linting/formatting:
  - ktlint: ./gradlew ktlintCheck | ./gradlew ktlintFormat
  - spotless (if configured): ./gradlew spotlessCheck | ./gradlew spotlessApply
- Static analysis:
  - detekt: ./gradlew detekt
  - Common suppressions:
    - LongMethod: @Suppress("LongMethod") or @file:Suppress("LongMethod")
    - UseCheckOrError: replace throw IllegalStateException(...) with check(...) or error(...)
    - Indentation (ktlint): @file:Suppress("ktlint:standard:indent") or run formatter
- JVM toolchains (project-wide recommendation):
  - Align Java/Kotlin targets via toolchains to avoid mismatches.
- Unit tests.

### With more time

- Make app demo work properly.
- Add more tests to cover all edge cases on connection and data transfer.
- Release Android and iOS ready to use SDKs
- Create a Flutter app example using the SDKs

### License
MIT
