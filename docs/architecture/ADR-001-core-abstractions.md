# ADR-001: Six Core Abstractions for a Testable Voice Pipeline

- **Status:** Accepted (2026-10-27). To be realised by PR 2 of the
  refactor-for-testability sequence (`docs/architecture/pr-plan.md`).
- **Deciders:** project owner + android-architect agent.
- **Scope:** the six seams needed before any of the god-objects
  (`VoiceCoordinator`, `VoiceService`, `VoskEngine`, `VoiceCommandRepository`,
  `VoiceEffectsImpl`) can be split and unit-tested on the JVM.

---

## Context

The current voice pipeline is welded to Android platform APIs in six distinct
places. Each weld blocks JVM-level unit testing of the surrounding logic.

| Platform API                     | Welded into                                                                          | Why it blocks testing                                             |
| -------------------------------- | ------------------------------------------------------------------------------------ | ----------------------------------------------------------------- |
| `android.os.SystemClock`         | `VoiceCoordinator.kt:31`, `VoiceCoordinator.kt:82`, `VoiceService.kt:188`            | Real monotonic time; can't advance deterministically in a test.   |
| `android.os.Handler`             | `VoiceCoordinator.kt:13`, `VoiceCoordinator.kt:125-132`, `AppModule.kt:72`           | Posts to the main looper; needs Robolectric even for a stopwatch. |
| `androidx.localbroadcastmanager` | `VoiceEffectsImpl.kt:46`, `VoiceService.kt:233`, `MainActivity.kt:49-88`             | Static singleton; implicit global state; deprecated.              |
| `android.util.Log`               | `VoiceCoordinator.kt:5`, `VoiceEffectsImpl.kt:5`, `VoskEngine.kt:4`, and 9 others    | Static class; cannot assert on log output in JVM tests.           |
| `org.vosk.Recognizer` / `Model`  | `VoskEngine.kt:19-37` (`lateinit var`), construction coupled to `Context.cacheDir`   | Native library; cannot run on the JVM; no interface to fake.      |
| `android.media.AudioRecord`      | `AudioRecorder.kt:17-73`                                                             | Opens the mic; cannot run headless; no way to inject scripted PCM. |

Beyond this, the current code also duplicates `normalize` in three places
(`VoiceCoordinator.kt:140`, `VoiceEffectsImpl.kt:85`,
`VoiceCommandRepository.kt:96-100`), hard-codes three magic numbers
(`UI_THROTTLE_MS = 250`, `WAKE_DEBOUNCE_MS = 1200`,
`commandTimeoutMs = 10_000` at `VoiceCoordinator.kt:20-22`), and ships a
single Russian Vosk model from `assets/vosk-model-small-ru/` into
`cacheDir` via `VoskEngine.modelPath()` (`VoskEngine.kt:71-90`). Those are
handled by subsequent refactor PRs; this ADR only covers the six seams
that must land first.

**Testing goal we are optimising for:** `VoiceCoordinator`,
`VoiceStateMachine`, `WakeParser`, `VoicePipeline`, `VoiceEffectsImpl`, and
each repository must be unit-testable on the pure JVM (no Robolectric, no
device, no mic) with virtual time and scripted PCM. Only the thin adapters
(`AndroidClock`, `HandlerScheduler`, `AndroidLogger`,
`VoskSpeechRecognizer`, `AndroidAudioSource`, `RecognizerStatusBus`) are
allowed to touch Android APIs, and they are tested at the Robolectric
level or not at all.

---

## Decision

Introduce six interfaces under `ru.lsn03.voicemediacontroller.core.*`.
Every production class that today reaches for an Android symbol instead
depends on one of these interfaces. Each interface has exactly one
production implementation (an Android adapter) and at least one in-memory
test double.

### 1. `Clock`

```kotlin
package ru.lsn03.voicemediacontroller.core.time

interface Clock {
    fun elapsedRealtimeMs(): Long
}

// Production adapter
class AndroidClock : Clock {
    override fun elapsedRealtimeMs(): Long
}

// Test double (lives under src/test)
class FakeClock(initialMs: Long = 0L) : Clock {
    override fun elapsedRealtimeMs(): Long
    fun advanceBy(ms: Long)
    fun set(ms: Long)
}
```

Replaces direct calls to `android.os.SystemClock.elapsedRealtime()` in
`VoiceCoordinator.kt:31` and `VoiceCoordinator.kt:82`. The `SystemClock`
usage in `VoiceService.onTaskRemoved` (`VoiceService.kt:188`) stays in the
service shell for now - it belongs to the alarm-restart path which is
not exercised by unit tests.

### 2. `Scheduler`

```kotlin
package ru.lsn03.voicemediacontroller.core.time

interface Scheduler {
    fun postDelayed(delayMs: Long, token: Any, action: () -> Unit)
    fun cancel(token: Any)
    fun cancelAll()
}

// Production adapter
class HandlerScheduler(private val handler: Handler) : Scheduler

// Test double
class FakeScheduler(private val clock: FakeClock) : Scheduler {
    fun advanceBy(ms: Long)
    fun pending(): List<Pair<Any, Long>>
}
```

Replaces the `Handler + Runnable` pair in `VoiceCoordinator.kt:26` and
`VoiceCoordinator.kt:125-132`. Token-keyed cancel replaces the current
`handler.removeCallbacks(timeoutRunnable)` pattern - we gain the ability
to represent **multiple distinct scheduled actions** safely, which the
pipeline will need as soon as we add e.g. a "stop ducking after N ms"
timer.

### 3. `TextPublisher`

```kotlin
package ru.lsn03.voicemediacontroller.core.text

fun interface TextPublisher {
    fun publish(text: String)
}
```

Replaces `LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent)` in
`VoiceEffectsImpl.kt:42-47` and `VoiceService.kt:229-234`. Production
binding (PR 5) is a `RecognizerStatusBus` singleton exposing
`StateFlow<RecognizerStatus>`; test double is a `RecordingTextPublisher`
that captures the ordered list of published strings.

Explicitly a `fun interface`: at most one method, no state, trivially
composable - a status bus implements it; so does a logger-publisher
hybrid in tests.

### 4. `AppLogger`

```kotlin
package ru.lsn03.voicemediacontroller.core.log

interface AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, cause: Throwable? = null)
    fun e(tag: String, message: String, cause: Throwable? = null)
}

// Production
class AndroidLogger : AppLogger

// Test double
class RecordingLogger : AppLogger {
    data class Entry(val level: Char, val tag: String, val message: String, val cause: Throwable?)
    val entries: List<Entry>
    fun clear()
}
```

Replaces 12+ direct `android.util.Log` call sites across
`VoiceCoordinator.kt`, `VoiceEffectsImpl.kt`, `VoiceCommandRepository.kt`,
`VoskEngine.kt`, `VoiceService.kt`, and `AudioRecorder.kt`. Tag string is
preserved so the migration is mechanical (search `Log.d(APPLICATION_NAME`
and swap).

### 5. `SpeechRecognizer`

```kotlin
package ru.lsn03.voicemediacontroller.core.speech

sealed interface SpeechResult {
    data class Final(val text: String) : SpeechResult
    data class Partial(val text: String) : SpeechResult
    data object None : SpeechResult
}

interface SpeechRecognizer {
    fun updateGrammars(wakeJson: String, commandJson: String, wakeCommandJson: String)
    fun acceptWake(pcm: ByteArray): SpeechResult
    fun acceptCommand(pcm: ByteArray): SpeechResult
    fun resetWake()
    fun resetCommand()
}

// Production
class VoskSpeechRecognizer(
    private val voskEngine: VoskEngine,
) : SpeechRecognizer

// Test double
class FakeSpeechRecognizer : SpeechRecognizer {
    fun scheduleWake(result: SpeechResult)
    fun scheduleCommand(result: SpeechResult)
    val lastGrammars: Triple<String, String, String>?
}
```

Replaces the direct `VoskEngine` dependency in `VoiceCoordinator.kt:11`
and `VoiceEffectsImpl.kt:22`. The native Vosk engine stays behind the
adapter; the sealed `SpeechResult` is a straight rename of the existing
`vosk.VoskResult` so no behaviour changes - only the import path.

### 6. `AudioSource`

```kotlin
package ru.lsn03.voicemediacontroller.core.audio

interface AudioSource {
    fun start(onPcm: (ByteArray) -> Unit, onError: (String) -> Unit)
    fun stop()
}

// Production
class AndroidAudioSource(
    private val sampleRate: Int,
) : AudioSource

// Test double
class FakeAudioSource : AudioSource {
    fun emit(pcm: ByteArray)
    fun emitError(message: String)
    val isRunning: Boolean
}
```

Replaces the standalone `service/AudioRecorder.kt` as a dependency of
`VoicePipeline` (PR 6). `AudioRecorder`'s body - the `AudioRecord` setup,
the PCM short->byte reinterpretation at `AudioRecorder.kt:52-56`, the
`AtomicBoolean` shutdown - moves into `AndroidAudioSource` unchanged.

---

## Consequences

### Testing payoff per abstraction

- **`Clock`** - `VoiceCoordinator` can now assert "fire two wake final
  results 1200 ms apart, only one triggers" without touching
  `SystemClock`. Debounce + throttle windows become unit-testable.
- **`Scheduler`** - the command-timeout path (10 s today) is deterministic:
  `FakeScheduler.advanceBy(10_000)` fires the timeout; no `Handler`, no
  Robolectric, no shadow looper. Enables the "timeout vs final race" test
  that is impossible today.
- **`TextPublisher`** - `VoiceEffectsImpl` and the service stop depending
  on a static `LocalBroadcastManager`. The pure test asserts the ordered
  list of publications given a scripted PCM feed. UI tests inject the
  same bus and assert state transitions, no broadcast receivers needed.
- **`AppLogger`** - tests can assert that specific warnings fire (e.g.
  "Wake debounce: ignored" at `VoiceCoordinator.kt:89`). Today those
  warnings are invisible to the test harness and silently removable.
- **`SpeechRecognizer`** - the coordinator becomes testable without JNI.
  `FakeSpeechRecognizer.scheduleWake(Final("джарвис"))` replaces an entire
  on-device recording session. The native Vosk code is tested only in its
  thin adapter under Robolectric.
- **`AudioSource`** - `VoicePipelineTest` drives a canned PCM stream
  (`FakeAudioSource.emit(ByteArray(1600) { ... })`) and asserts that
  recognised text, state, and status-bus updates all come out in the right
  order. No microphone permission, no device, no `AudioRecord`.

### Non-test consequences

- **Six new interfaces + six new adapters** add ~250 LOC of code the team
  did not previously write. Mitigated by the deletion of 3 `normalize`
  duplicates, the god-coordinator-glue code, and the entire
  `LocalBroadcastManager` scaffolding.
- **Hilt graph grows** by one module (`CoreModule`) with six `@Provides`.
  All six are `@Singleton`; the graph stays shallow.
- **Thread discipline becomes explicit**: `Scheduler` is defined as
  "callbacks run on the main looper" (because the production impl wraps
  `Handler(Looper.getMainLooper())`). Any future I/O-bound scheduling
  needs a separate seam - that is a feature, not a bug.
- **The native Vosk code paths lose test coverage directly** but gain it
  indirectly: everything upstream of `VoskSpeechRecognizer` is now
  JVM-testable, so a Vosk upgrade is a one-file-review change instead of
  a whole-pipeline review.
- **`.claude/` rule unchanged**: none of these files live under
  `.claude/`; the directory stays untracked and un-ignored.

---

## Alternatives considered (at least one rejected design per abstraction)

### `Clock`

- **Rejected:** use `kotlin.time.TimeSource.Monotonic` directly, no
  interface. Rejected because scheduling (`postDelayed` + `cancel`) still
  needs its own seam, so we would end up with two abstractions where one
  pair (`Clock` + `Scheduler`) covers both symmetrically. Also, the
  coordinator wants a `Long` ms count to match the existing comparison
  arithmetic at `VoiceCoordinator.kt:64` and `VoiceCoordinator.kt:83` -
  wrapping `TimeMark` is more ceremony than value.
- **Rejected:** inject raw `() -> Long`. Rejected because every call site
  would have to re-document "this is monotonic ms"; a named interface
  carries the contract.

### `Scheduler`

- **Rejected:** depend on `kotlinx.coroutines.delay` + a `CoroutineScope`.
  Rejected because `VoiceCoordinator.onPcm` is called from an audio
  thread, not a coroutine; migrating the hot path to coroutines just for
  one 10-second timeout adds context-switch cost on the PCM path and
  makes the state machine harder to reason about. Also, under test we
  want `advanceBy(ms)` without needing `runTest { ... }` for every
  assertion.
- **Rejected:** keep `Handler` as the type, hide it behind no interface
  but inject it. Rejected because `Handler` cannot be newed up on a JVM
  test; using Robolectric for every coordinator test is the exact tax
  we're trying to avoid.

### `TextPublisher`

- **Rejected:** keep `LocalBroadcastManager`, write a Robolectric test
  harness that intercepts broadcasts. Rejected because
  `LocalBroadcastManager` is deprecated and the whole app's observability
  surface pivoting on a deprecated API is an unforced architectural
  smell. We lose: cross-process support (which we don't need) and
  synchronous dispatch (which `StateFlow` conflation gives us anyway).
- **Rejected:** expose the `RecognizerStatusBus` directly to producers
  without the `TextPublisher` abstraction. Rejected because
  `VoiceEffectsImpl` has no business knowing it is talking to a status
  bus - it has one output, a text line, and the smaller the interface it
  depends on, the simpler its test.

### `AppLogger`

- **Rejected:** adopt Timber. Rejected because adding a third-party
  logger just to get a testable facade is heavier than a 4-method
  interface, and Timber still relies on static state (`Timber.plant`).
  Also, no existing code uses Timber; keeping the dep tree quiet matters
  for an Android app of this size.
- **Rejected:** `object Log` singleton with a swappable backend.
  Rejected because singletons are exactly what we are escaping; Hilt
  gives us singleton semantics with injectability for free.

### `SpeechRecognizer`

- **Rejected:** inject the native `org.vosk.Recognizer` through an
  interface. Rejected because the native type is `final`, its constructor
  requires a JNI-loaded `Model`, and we would need **three** recognizer
  instances (wake / command / wake+command) each with its own grammar -
  the useful seam is one level up, where the three-recognizer dance is
  internal to the adapter. Our interface therefore has `acceptWake` and
  `acceptCommand` as the two methods the pipeline actually calls, with
  `updateGrammars` as a single transactional update.
- **Rejected:** expose a `Flow<SpeechResult>` instead of pull-based
  `accept*` methods. Rejected because the PCM path is pull-driven today
  (one PCM frame -> one `accept` call) and converting to push adds
  back-pressure concerns we don't need. The pull API also matches the
  Vosk native API 1-to-1.

### `AudioSource`

- **Rejected:** expose a `Flow<ByteArray>` of PCM frames. Rejected
  because the existing `AudioRecorder` already uses a callback shape and
  the coordinator consumes PCM synchronously; wrapping in a `Flow` adds a
  buffering layer we don't need and complicates cancellation semantics
  on the audio thread.
- **Rejected:** keep `AudioRecorder` as a concrete class and inject it.
  Rejected because `AudioRecorder` touches `android.media.AudioRecord`
  which cannot run under JVM tests, so `VoicePipeline` would be forced
  into Robolectric. The interface lets us ship a `FakeAudioSource` with
  one line per scripted frame.

---

## Status

Accepted. First landing is PR 2 of the plan in
`docs/architecture/pr-plan.md`. Each later PR consumes one or more of
these abstractions:

- PR 3: `Clock`, `Scheduler`, `AppLogger`, `SpeechRecognizer`.
- PR 4: `AppLogger`.
- PR 5: `TextPublisher`.
- PR 6: `AudioSource`, `SpeechRecognizer`.
- PR 7a: `SpeechRecognizer` (model-loading moved behind `ModelProvider`,
  but the interface shape above is unaffected).

Supersession policy: this ADR stays in effect for the whole
refactor-for-testability initiative. Any later ADR that changes one of
these six interfaces must explicitly supersede this one and list the
migration cost per call site.
