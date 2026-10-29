# Android Order Tracker

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-Database-3DDC84?logo=android&logoColor=white)
![Tests: 13 passing](https://img.shields.io/badge/tests-13%20passing-brightgreen)
![License: MIT](https://img.shields.io/badge/license-MIT-blue)

Offline-first Android app for tracking delivery orders. Status updates write to the local Room database instantly, then sync to the server in the background. The app works without a network connection - pending changes queue up and sync when connectivity returns.

## Measured Results

| Metric | Value |
|--------|-------|
| Offline-first writes | **Instant** (Room first, API async) |
| Sync retry strategy | **WorkManager** with exponential backoff |
| Status transitions | **5-step** lifecycle (Placed → Delivered) |
| Tests | **13** unit tests (domain + repository) |
| Test coverage | Status transitions, offline sync, partial failure recovery |

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  Compose UI                      │
│                                                  │
│   ┌──────────────┐       ┌───────────────────┐   │
│   │  Order List  │       │  Order Detail      │   │
│   │  Screen      │       │  Screen            │   │
│   └──────┬───────┘       └─────────┬─────────┘   │
└──────────┼─────────────────────────┼─────────────┘
           │ observe Flow            │ updateStatus()
           ▼                         ▼
┌─────────────────────────────────────────────────┐
│                  ViewModel                       │
│                                                  │
│   Orders LiveData ←── Room Flow                  │
│   Status actions  ──→ Repository calls           │
└────────────────────────┬────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────┐
│              OrderRepository                     │
│                                                  │
│   1. write ──→ Room        (instant, local)      │
│   2. try   ──→ API         (async, may fail)     │
│   3. flag  ──→ pendingSync = true  (on failure)  │
└───────┬──────────────┬──────────────┬───────────┘
        │              │              │
        ▼              ▼              ▼
┌────────────┐  ┌────────────┐  ┌───────────────┐
│    Room    │  │  Retrofit  │  │  WorkManager  │
│   (SoT)   │  │    API     │  │  SyncWorker   │
│            │  │            │  │               │
│ Flow<List> │  │ GET /orders│  │ pendingSync   │
│ insert()   │  │ PUT /status│  │ rows → API    │
│ update()   │  │            │  │ mark synced   │
└────────────┘  └────────────┘  └───────────────┘
```

**SoT** = Single Source of Truth. The UI always reads from Room, never directly from the API.

## Status State Machine

```
 ┌──────────┐     ┌───────────┐     ┌───────────┐     ┌─────────────────┐     ┌───────────┐
 │  Placed  ├────→│ Confirmed ├────→│ Preparing ├────→│ Out for         ├────→│ Delivered │
 └────┬─────┘     └─────┬─────┘     └─────┬─────┘     │ Delivery        │     │ (terminal)│
      │                 │                 │           └─────────────────┘     └───────────┘
      │                 │                 │
      └─────────────────┴─────────────────┘
                        │
                        ▼
                 ┌────────────┐
                 │ Cancelled  │
                 │ (terminal) │
                 └────────────┘

  next()  returns the single valid next state, or null at terminal states.
  The UI shows or hides the "Advance" button based on next().
```

## Design Decisions

<details>
<summary><strong>1. Offline-first write path</strong></summary>

`updateStatus()` writes to Room immediately, then attempts the API call. If the API fails, the order is flagged `pendingSync = true` and the change is visible to the user right away. A `SyncWorker` picks up pending changes when connectivity returns.

</details>

<details>
<summary><strong>2. Room as single source of truth</strong></summary>

The UI observes `Flow<List<Order>>` from Room, never from the API directly. `refresh()` fetches from the API and upserts into Room. This means the order list and detail screen always render from local state, with no loading spinners for cached data.

</details>

<details>
<summary><strong>3. Explicit status state machine</strong></summary>

`OrderStatus.next()` returns the only valid next state, or null for terminal states (Delivered, Cancelled). The UI uses this to show or hide the "Advance" button. There is no way to skip a state or go backwards through the normal flow.

</details>

<details>
<summary><strong>4. WorkManager for background sync</strong></summary>

`SyncWorker` runs as a periodic `CoroutineWorker` with constraints on network availability. It iterates over `pendingSync = true` rows, syncs each one, and marks it synced. Partial failures leave the remaining rows for the next run. WorkManager handles retry with backoff.

</details>

<details>
<summary><strong>5. Fake-based testing over mocks</strong></summary>

Tests use hand-written `FakeOrderDao` and `FakeOrderApi` implementations rather than mocking libraries. Fakes implement the actual interface and maintain in-memory state, so tests verify behavior (offline writes persist, partial sync retries) rather than interaction patterns.

</details>

## Quick Start

```bash
# Open in Android Studio
# Build > Make Project

# Run unit tests
./gradlew testDebugUnitTest

# Run on emulator
./gradlew installDebug
```

## Tests

```
domain/OrderStatusTest       5 tests  transitions, terminal states, full lifecycle
data/OrderRepositoryTest     8 tests  refresh, offline updates, pending sync,
                                      partial failure, observe flow, sync count
```

## Project Structure

```
app/src/main/java/com/siddhant/ordertracker/
  domain/model/     Order, OrderItem, OrderStatus, StatusUpdate
  data/
    local/           Room entities, DAO, database
    remote/          Retrofit API interface, DTOs
    repository/      OrderRepository (offline-first)
    sync/            WorkManager SyncWorker
  ui/
    orders/          Order list screen + ViewModel (Compose)
    detail/          Order detail screen + ViewModel (Compose)

app/src/test/
  domain/            OrderStatus transition tests
  data/              Repository tests with fakes
```

## License

MIT
