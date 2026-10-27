# Refactor-for-Testability PR Plan

This document is the authoritative, ordered PR sequence for the
"refactor-for-testability" initiative on `voice-media-controller`.

Context assumed:

- Start branch: `feature/architecture-foundation` (already cut from `develop`).
- Each PR below is cut from `develop` into its own `feature/<slug>` branch and
  merged back to `develop` via PR. No PR targets `main` or `release/*`.
- Each PR is sized to **~400 LOC of diff maximum** (production + test code),
  not counting generated files, resource moves, or vendored binaries.
- All PRs preserve observable UX: wake-word still works, foreground service
  still runs, phrase CRUD still works. If UX must change, the PR says so.
- Commit author identity: `Lvov Sergei <lvov.sn03@gmail.com>`. No
  AI-attribution trailers. English commit subjects, Conventional-Commits-ish.
- `.claude/` must never appear in a diff and must never be added to
  `.gitignore`.

Current-state anchors (so a reviewer can audit this plan against the code):

- `app/src/main/java/ru/lsn03/voicemediacontroller/voice/VoiceCoordinator.kt:10-143` -
  god-coordinator welded to `android.os.Handler`, `android.os.SystemClock`,
  `android.util.Log`; magic numbers `UI_THROTTLE_MS = 250`,
  `WAKE_DEBOUNCE_MS = 1200`, `commandTimeoutMs = 10_000`; fallback wake word
  `"джарвис"` hard-coded at lines 16 and 44.
- `app/src/main/java/ru/lsn03/voicemediacontroller/voice/VoiceEffectsImpl.kt:42-47` -
  publishes recognised text via `LocalBroadcastManager`.
- `app/src/main/java/ru/lsn03/voicemediacontroller/voice/VoiceEffectsImpl.kt:85` -
  private `normalize` (duplicate #1).
- `app/src/main/java/ru/lsn03/voicemediacontroller/voice/VoiceCommandRepository.kt:96-100` -
  private `normalize` (duplicate #2); same file does phrases, wake word,
  grammar JSON building, and defaults seeding - god repo.
- `app/src/main/java/ru/lsn03/voicemediacontroller/voice/VoiceCommandRepository.kt:107-112` -
  fallback `"джарвис"` baked into the initial `StateFlow` value.
- `app/src/main/java/ru/lsn03/voicemediacontroller/vosk/VoskEngine.kt:14-90` -
  lateinit `Model` / `Recognizer`, model copied from `assets/` to `cacheDir`,
  no `SpeechRecognizer` seam.
- `app/src/main/java/ru/lsn03/voicemediacontroller/service/VoiceService.kt:36-253` -
  god-service: lifecycle, notifications, DI wiring, grammar collection,
  broadcast publishing, PCM wiring, restart alarm.
- `app/src/main/java/ru/lsn03/voicemediacontroller/service/AudioRecorder.kt:11-89` -
  blocking thread + raw `AudioRecord`; no `AudioSource` seam.
- `app/src/main/java/ru/lsn03/voicemediacontroller/MainActivity.kt:49-88` -
  UI subscribes to `LocalBroadcastManager`.
- `app/build.gradle.kts:49-88` - test deps are only `junit`,
  `androidx.junit`, `espresso.core`, `ui-test-junit4`. Zero JVM test infra for
  coroutines / Robolectric / Truth / MockK / Turbine.
- `app/src/main/java/ru/lsn03/voicemediacontroller/utils/Utilities.kt:5` -
  `MODEL_NAME = "vosk-model-small-ru"` and the model lives under
  `app/src/main/assets/vosk-model-small-ru/` (currently shipped in APK).

---

## PR 1 - Test infrastructure + pure-logic smoke tests

### Branch

`feature/test-infrastructure` -> `develop`.

### Scope

Files touched:

- `gradle/libs.versions.toml` - add versions and library aliases for
  `kotlinx-coroutines-test`, `turbine`, `truth` (or `assertk`), `mockk`,
  `robolectric`, `androidx-test-core`, `androidx-arch-core-testing`,
  `androidx-room-testing`, `mockwebserver`. Keep existing `junit = 4.13.2`.
- `app/build.gradle.kts` - wire the above as `testImplementation` /
  `androidTestImplementation`; apply JaCoCo plugin and a single
  `jacocoTestReport` task that merges unit-test coverage per build type.
- `app/src/test/.../ExampleUnitTest.kt` - delete.
- `app/src/androidTest/.../ExampleInstrumentedTest.kt` - delete.
- NEW `app/src/test/java/ru/lsn03/voicemediacontroller/text/TextNormalizerTest.kt` -
  pins the normalisation contract that today is duplicated across
  `VoiceCoordinator.normalize`, `VoiceEffectsImpl.normalize`,
  `VoiceCommandRepository.normalize`. First we lock behaviour, then PR 3/4
  removes duplicates.
- NEW `app/src/test/java/ru/lsn03/voicemediacontroller/command/CommandMatcherTest.kt` -
  pins current matcher semantics (exact phrase membership from
  `CommandMatcher.kt:5-9`).
- NEW `app/src/test/java/ru/lsn03/voicemediacontroller/voice/GrammarBuilderCharacterizationTest.kt` -
  snapshot of the JSON currently emitted by
  `VoiceCommandRepository.observeVoskGrammars` for a 2-phrase, 1-wake-word
  fixture. Characterization only - we are freezing behaviour before extraction.

No production Kotlin files are moved or edited in PR 1. The three duplicate
`normalize` functions stay as they are; PR 3 and PR 4 remove them.

### Public API delta

```kotlin
// No new public API in production code.
// Only test fixtures appear.
```

### Exit criteria

- `./gradlew :app:assembleDebug` green.
- `./gradlew :app:testDebugUnitTest` runs the three new test classes and
  passes (minimum 12 test methods total).
- `./gradlew :app:jacocoTestReport` produces an HTML report at
  `app/build/reports/jacoco/jacocoTestReport/html/index.html`.
- Baseline coverage recorded in PR description (expected ~2-4 % line coverage;
  this is the baseline every later PR must beat).
- No change to any file under `app/src/main/java/**` other than the two
  `Example*Test` deletions.
- No `.claude/` entry in diff; no `.claude` entry in `.gitignore`.

### Risk

- **Risk:** AGP `8.10.0-alpha05` + JaCoCo may need a toolchain pin.
  **Mitigation:** pin JaCoCo to a known-good `0.8.12` version and run the
  task locally before pushing. If AGP refuses, downgrade JaCoCo one minor.
- **Risk:** Robolectric pulled in now (even if unused in PR 1) can slow CI.
  **Mitigation:** add it under `testImplementation` only; do not force
  `useRobolectric()` on `testOptions` until PR 4.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| JVM unit | `TextNormalizerCharacterizationTest` | hard-coded strings; mirrors the three current `normalize` bodies. |
| JVM unit | `CommandMatcherTest` | in-memory `List<CommandBinding>`. |
| JVM unit | `GrammarBuilderCharacterizationTest` | hard-coded wake word + 2 phrases; asserts exact JSON strings currently produced. |

### Hand-off

Later PRs may assume:

- `kotlinx-coroutines-test`, `turbine`, `truth` (or `assertk`), `mockk`,
  `robolectric`, `androidx-room-testing`, `mockwebserver` are all on the
  classpath.
- `jacocoTestReport` task exists and merges unit coverage.
- The three characterization tests exist and will FAIL if a later PR
  changes the behaviour they pin. PR 3 and PR 4 must therefore update them
  (not delete them) when they consolidate `normalize` and extract
  `GrammarBuilder`.

---

## PR 2 - Core abstractions + `WakeParser` extraction

### Branch

`feature/core-abstractions` -> `develop`.

### Scope

New files (all under `app/src/main/java/ru/lsn03/voicemediacontroller/core/`):

- `core/time/Clock.kt`
- `core/time/Scheduler.kt`
- `core/time/AndroidClock.kt` - `SystemClock.elapsedRealtime()` wrapper.
- `core/time/HandlerScheduler.kt` - `android.os.Handler`-backed Scheduler.
- `core/log/AppLogger.kt`
- `core/log/AndroidLogger.kt` - `android.util.Log` wrapper.
- `core/text/TextPublisher.kt`
- `core/audio/AudioSource.kt` - PCM stream abstraction (not wired yet).
- `core/speech/SpeechRecognizer.kt`
- `core/speech/VoskSpeechRecognizer.kt` - adapter around existing
  `VoskEngine` (keeps `VoskEngine` internal to the adapter).
- `voice/WakeParser.kt` - pure parser lifted from
  `VoiceCoordinator.handleWakePcm` (`VoiceCoordinator.kt:74-117`).

New DI binding file:

- `di/CoreModule.kt` - binds `Clock`, `Scheduler`, `AppLogger` as
  `@Singleton`. `TextPublisher`, `SpeechRecognizer`, `AudioSource`
  implementations are bound in PR 5 / PR 6 (concrete adapters land here but
  stay unused by production code until the pipeline migration).

Edits to existing files:

- `di/AppModule.kt` - unchanged in PR 2 (the new bindings live in
  `CoreModule`). The existing `provideMainHandler` stays; `HandlerScheduler`
  takes `Handler` by DI.

Nothing in `VoiceCoordinator.kt` / `VoiceEffectsImpl.kt` / `VoiceService.kt`
changes yet. The abstractions ship behind DI unused by production code
except via the new `WakeParser` tests. (We prove the seams work before we
start swapping call sites in PR 3.)

### Public API delta

```kotlin
package ru.lsn03.voicemediacontroller.core.time

interface Clock {
    fun elapsedRealtimeMs(): Long
}

interface Scheduler {
    fun postDelayed(delayMs: Long, token: Any, action: () -> Unit)
    fun cancel(token: Any)
    fun cancelAll()
}

package ru.lsn03.voicemediacontroller.core.log

interface AppLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, cause: Throwable? = null)
    fun e(tag: String, message: String, cause: Throwable? = null)
}

package ru.lsn03.voicemediacontroller.core.text

fun interface TextPublisher {
    fun publish(text: String)
}

package ru.lsn03.voicemediacontroller.core.audio

interface AudioSource {
    fun start(onPcm: (ByteArray) -> Unit, onError: (String) -> Unit)
    fun stop()
}

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

package ru.lsn03.voicemediacontroller.voice

sealed interface WakeOutcome {
    data object Nothing : WakeOutcome
    data class WakeHeard(val titleCased: String) : WakeOutcome
    data class WakeThenCommand(val command: String) : WakeOutcome
    data class HeardPartial(val text: String) : WakeOutcome
}

class WakeParser(private val wakeWord: () -> String) {
    fun parseFinal(normalizedText: String): WakeOutcome
    fun parsePartial(partialText: String): WakeOutcome
}
```

### Exit criteria

- Build green.
- `./gradlew :app:testDebugUnitTest` passes with ~25+ new unit tests
  covering `WakeParser`, `FakeClock`, `FakeScheduler`, `RecordingLogger`.
- `./gradlew :app:assembleDebug` still produces a working APK (no behaviour
  change; abstractions are inert).
- Coverage report shows the `core.*` and `voice.WakeParser` packages at
  **>= 90 % line coverage**.

### Risk

- **Risk:** `HandlerScheduler` + token semantics subtly diverge from
  `handler.removeCallbacks(Runnable)` (we switch from Runnable identity to a
  token map). **Mitigation:** `HandlerSchedulerTest` under Robolectric
  verifies cancel-by-token on main looper.
- **Risk:** `VoskSpeechRecognizer` becomes a dead wrapper for one PR.
  **Mitigation:** PR 3 immediately consumes it from the refactored
  coordinator - keep the gap to one PR.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| JVM unit | `WakeParserTest` | hard-coded wake word "джарвис"; tests exact match, prefix "wake cmd", unknown, blank. |
| JVM unit | `FakeClockTest` | self-test of the test double. |
| JVM unit | `FakeSchedulerTest` | asserts advance-by-time semantics and cancel-by-token. |
| JVM unit | `RecordingLoggerTest` | self-test; captures emitted lines in order. |
| Robolectric | `HandlerSchedulerTest` | real `Handler(Looper.getMainLooper())`, shadow looper advanced manually. |
| Robolectric | `VoskSpeechRecognizerAdapterTest` | mocks `VoskEngine`; verifies pass-through of grammars/accept/reset. |

### Hand-off

PR 3 assumes:

- `Clock`, `Scheduler`, `AppLogger`, `SpeechRecognizer`, `WakeParser`
  exist and are injectable via Hilt.
- Test doubles `FakeClock`, `FakeScheduler`, `RecordingLogger`,
  `FakeSpeechRecognizer` exist under
  `app/src/test/java/ru/lsn03/voicemediacontroller/core/fakes/`.

---

## PR 3 - `VoiceStateMachine` + `VoiceCoordinator` refactor

### Branch

`feature/voice-state-machine` -> `develop`.

### Scope

New files:

- `voice/VoiceStateMachine.kt` - pure state machine; no Android deps; the
  three transitions currently inlined in `VoiceCoordinator.transitionTo`
  (`VoiceCoordinator.kt:119-138`) move here.
- `voice/VoiceCoordinatorConfig.kt` - data class holding
  `uiThrottleMs = 250`, `wakeDebounceMs = 1200`, `commandTimeoutMs = 10_000`.
  Delete the magic numbers from `VoiceCoordinator.kt:20-22`.
- `text/TextNormalizer.kt` - the single, canonical normaliser. Delete the
  three duplicates in `VoiceCoordinator.kt:140`,
  `VoiceEffectsImpl.kt:85`, `VoiceCommandRepository.kt:96-100`.

Edited files:

- `voice/VoiceCoordinator.kt` - becomes thin orchestration over
  `WakeParser` + `VoiceStateMachine` + `SpeechRecognizer` + `Clock` +
  `Scheduler` + `AppLogger`. All `android.*` imports removed from this file.
- `di/ServiceModule.kt` - `provideVoiceCoordinator` gains `Clock`,
  `Scheduler`, `AppLogger`, `TextNormalizer`, `VoiceStateMachine`,
  `VoiceCoordinatorConfig`.
- `di/AppModule.kt` - add `provideVoiceCoordinatorConfig` with the three
  constants (one Hilt provide, no magic numbers in logic classes).
- `voice/VoiceEffectsImpl.kt` - `normalize` private helper deleted;
  constructor gains `TextNormalizer`.
- `voice/VoiceCommandRepository.kt` - `normalize` private helper deleted;
  constructor gains `TextNormalizer`. (This file still owns everything else;
  it is split in PR 4.)

### Public API delta

```kotlin
package ru.lsn03.voicemediacontroller.voice

enum class VoiceStateEvent { WakeHeard, CommandRecognised, CommandTimeout, Reset }

class VoiceStateMachine(initial: VoiceState = VoiceState.WAKE_LISTENING) {
    val state: VoiceState
    fun on(event: VoiceStateEvent): VoiceState
}

data class VoiceCoordinatorConfig(
    val uiThrottleMs: Long,
    val wakeDebounceMs: Long,
    val commandTimeoutMs: Long,
)

package ru.lsn03.voicemediacontroller.text

class TextNormalizer {
    fun normalize(raw: String): String
}

// VoiceCoordinator new constructor:
class VoiceCoordinator(
    private val recognizer: SpeechRecognizer,
    private val effects: VoiceEffects,
    private val wakeParser: WakeParser,
    private val stateMachine: VoiceStateMachine,
    private val scheduler: Scheduler,
    private val clock: Clock,
    private val logger: AppLogger,
    private val normalizer: TextNormalizer,
    private val config: VoiceCoordinatorConfig,
) {
    fun onPcm(pcm: ByteArray)
    fun setWakeWord(word: String)
    fun onTimeout()
}
```

### Exit criteria

- Build green; APK produced; manual smoke on device: wake-word still
  triggers, command still executes, timeout still returns to WAKE.
- Characterization tests from PR 1 still pass (behaviour unchanged).
- `./gradlew :app:testDebugUnitTest` adds >= 20 new tests for
  `VoiceStateMachine`, `VoiceCoordinator`, `TextNormalizer`.
- `voice.VoiceStateMachine` and `voice.VoiceCoordinator` reach
  **>= 85 % line coverage** in JaCoCo.
- `VoiceCoordinator.kt` has **zero** `import android.*` lines
  (grep check in PR description).

### Risk

- **Risk:** switching from Runnable to token-keyed `Scheduler.cancel`
  reorders cancel-vs-schedule and breaks the SWITCHING transient.
  **Mitigation:** `VoiceCoordinatorTest` has an explicit "rapid wake twice
  within debounce" scenario and a "command final arrives after timeout"
  race scenario. Both must pass before merge.
- **Risk:** removing the duplicate `normalize` subtly changes grammar JSON
  because `VoiceCommandRepository.normalize` collapses whitespace while
  the other two don't. **Mitigation:** `TextNormalizer` adopts the
  **repository** behaviour (trim + lowercase + collapse `\s+` -> ` `) and
  the characterization tests from PR 1 are updated in this PR to assert
  the new (strictly superior) output. Call this out in the PR description.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| JVM unit | `VoiceStateMachineTest` | pure Kotlin; exhaustive transition table test. |
| JVM unit | `TextNormalizerTest` | updated characterization from PR 1. |
| JVM unit | `VoiceCoordinatorTest` | `FakeClock`, `FakeScheduler`, `FakeSpeechRecognizer`, `RecordingLogger`, `RecordingVoiceEffects`. Scenarios: wake-only, wake+cmd one-shot, debounce, UI throttle, command timeout, timeout-vs-final race, rapid wake twice. |

### Hand-off

PR 4 assumes:

- `TextNormalizer` is the single source of truth; nothing else re-implements
  it.
- `VoiceCommandRepository` constructor already takes `TextNormalizer` so the
  split in PR 4 can propagate it to the sub-repositories.

---

## PR 4 - Repository split + Room tests

### Branch

`feature/repository-split` -> `develop`.

### Scope

New files:

- `voice/repo/VoicePhraseRepository.kt` - phrase CRUD only.
- `voice/repo/WakeWordRepository.kt` - wake-word get/set + observe.
- `voice/repo/GrammarRepository.kt` - takes `VoicePhraseRepository` +
  `WakeWordRepository` + a `GrammarBuilder` (extracted) and exposes
  `observeGrammars(): Flow<VoskGrammars>`.
- `voice/GrammarBuilder.kt` - pure JSON builder lifted from
  `VoiceCommandRepository.observeVoskGrammars`
  (`VoiceCommandRepository.kt:127-139`) and `toVoskJsonArray`
  (`VoiceCommandRepository.kt:161-175`). Takes `TextNormalizer`,
  `AppLogger`.

Edited files:

- `voice/VoiceCommandRepository.kt` - deleted; call sites move to the
  three new repositories. (Reviewer must verify the deletion.)
- `di/DatabaseModule.kt` - add `@Provides` for the three new
  repositories; keep `VoiceCommandDao` provider.
- `di/ServiceModule.kt` - `provideVoiceEffects` now takes
  `VoicePhraseRepository`; grammar collection in `VoiceService` switches to
  `GrammarRepository.observeGrammars()`.
- `ui/screens/home/HomeViewModel.kt` - `repo: VoiceCommandRepository` ->
  `wakeRepo: WakeWordRepository`.
- `ui/screens/commands/*` and `ui/screens/settings/*` - swap over to
  `VoicePhraseRepository` / `WakeWordRepository`.
- `service/VoiceService.kt` - swap `repo.observeWakeWord` ->
  `wakeRepo.observe`; swap `repo.grammars` -> `grammarRepo.observeGrammars`.

### Public API delta

```kotlin
package ru.lsn03.voicemediacontroller.voice.repo

interface VoicePhraseRepository {
    fun observePhrases(action: VoiceAction): Flow<List<VoicePhraseEntity>>
    fun observeAllEnabled(): Flow<List<VoicePhraseEntity>>
    suspend fun addPhrase(action: VoiceAction, phrase: String): Result<Unit>
    suspend fun setPhraseEnabled(id: Long, enabled: Boolean): Result<Unit>
    suspend fun deletePhrase(id: Long): Result<Unit>
    suspend fun updatePhraseText(id: Long, newText: String): Result<Unit>
    suspend fun resetToDefaults(action: VoiceAction): Result<Unit>
    fun observeBindings(): Flow<List<CommandBinding>>
}

interface WakeWordRepository {
    fun observe(): Flow<String>
    suspend fun set(newWord: String)
}

interface GrammarRepository {
    fun observeGrammars(): Flow<VoskGrammars>
}

package ru.lsn03.voicemediacontroller.voice

class GrammarBuilder(
    private val normalizer: TextNormalizer,
    private val logger: AppLogger,
) {
    fun build(wakeWord: String, phrases: List<String>): VoskGrammars
}
```

### Exit criteria

- Build green; APK produced; manual smoke: phrase add/edit/delete,
  reset-to-defaults, wake-word change, all still work and still drive the
  recognizer grammars.
- Coverage for `voice.repo.*` and `voice.GrammarBuilder` **>= 85 %**.
- Room-level tests run under Robolectric with an in-memory DB and >= 15
  test methods.
- `VoiceCommandRepository.kt` no longer exists in the tree
  (grep check in PR description).
- Characterization test `GrammarBuilderCharacterizationTest` from PR 1 is
  moved to `voice/GrammarBuilderCharacterizationTest.kt` and still passes
  (unchanged assertions).

### Risk

- **Risk:** `GrammarRepository.observeGrammars` must preserve
  `.distinctUntilChanged().debounce(300)` from
  `VoiceCommandRepository.kt:138-139`, else the recognizer thrashes.
  **Mitigation:** explicit `GrammarRepositoryTest` using
  `kotlinx-coroutines-test` `runTest` with virtual time asserts the
  debounce behaviour.
- **Risk:** fallback `"джарвис"` currently appears in **three** places
  (`VoiceCoordinator.kt:16`, `VoiceCoordinator.kt:44`,
  `VoiceCommandRepository.kt:111`, `VoiceCommandRepository.kt:125`,
  `VoiceService.kt:89`, `VoiceService.kt:237`,
  `VoiceWakePhraseSettingEntity.kt:9`, `DatabaseModule.kt:36`). Deduping is
  tempting but out of scope here. **Mitigation:** PR 4 introduces
  `WakeWordRepository.DEFAULT_WAKE_WORD = "джарвис"` as a `const` and
  only `WakeWordRepository` and the Room entity default reference it;
  `VoiceService` and `VoiceCoordinator` stop embedding the literal.
  Localisation of this constant is PR 8.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| Robolectric | `VoicePhraseRepositoryTest` | `Room.inMemoryDatabaseBuilder(...)`; covers add/duplicate/last-enabled/delete-last/update. |
| Robolectric | `WakeWordRepositoryTest` | in-memory DB; verifies default + fallback. |
| JVM unit | `GrammarBuilderTest` | pure; empty-phrases, one-phrase, duplicate-phrases, length-guard (`MAX_JSON_CHARS`). |
| JVM unit | `GrammarRepositoryTest` | `kotlinx-coroutines-test`; two `MutableStateFlow` fakes; asserts distinct + debounce semantics with virtual time. |

### Hand-off

PR 5 assumes:

- UI code is no longer coupled to a god-repo; `HomeViewModel` only reads
  wake word.
- `GrammarRepository.observeGrammars` is the ONLY source of grammars for
  the service.

---

## PR 5 - `RecognizerStatusBus` + UI migration off `LocalBroadcastManager`

### Branch

`feature/recognizer-status-bus` -> `develop`.

### Scope

New files:

- `voice/status/RecognizerStatus.kt` - sealed hierarchy.
- `voice/status/RecognizerStatusBus.kt` - `StateFlow<RecognizerStatus>`
  bus; app-singleton; implements `TextPublisher` (from PR 2) by mapping
  text events onto bus states.
- `ui/screens/home/HomeViewModel.kt` edits - subscribe to the bus.

Edited files:

- `voice/VoiceEffectsImpl.kt` - `publishText` goes through the injected
  `TextPublisher` (the bus). `LocalBroadcastManager` import gone.
- `service/VoiceService.kt` - `publishRecognizedText` deleted; it now goes
  via `TextPublisher`. `androidx.localbroadcastmanager` import gone.
- `MainActivity.kt` - `BroadcastReceiver` deleted; subscribe to the bus
  via a `HiltViewModel` and surface its state to `AppRoot`.
- `events/VoiceEvents.kt` - deleted (grep check).
- `app/build.gradle.kts` + `libs.versions.toml` - remove
  `androidx.localbroadcastmanager` dependency and version.

### Public API delta

```kotlin
package ru.lsn03.voicemediacontroller.voice.status

sealed interface RecognizerStatus {
    data object Idle : RecognizerStatus
    data class WakeHint(val partial: String) : RecognizerStatus
    data class WakeAccepted(val wakeTitleCased: String) : RecognizerStatus
    data class CommandHint(val partial: String) : RecognizerStatus
    data class CommandExecuting(val command: String) : RecognizerStatus
    data class Error(val message: String) : RecognizerStatus
}

@Singleton
class RecognizerStatusBus @Inject constructor() : TextPublisher {
    val status: StateFlow<RecognizerStatus>
    override fun publish(text: String)
    fun post(status: RecognizerStatus)
}
```

### Exit criteria

- Build green; APK produced; manual smoke: UI still shows "Жду команду" /
  "Слышу: ..." / "Выполняю: ..." as before (string-for-string identical).
- Zero references to `LocalBroadcastManager` in the codebase (grep check).
- Zero references to `VoiceEvents` in the codebase (grep check).
- Dependency on `androidx.localbroadcastmanager` removed from
  `libs.versions.toml`.
- New tests: `RecognizerStatusBusTest` + `HomeViewModelTest` using Turbine.

### Risk

- **Risk:** losing the ordering guarantee of `LocalBroadcastManager`
  (synchronous dispatch). **Mitigation:** `RecognizerStatusBus` uses
  `MutableStateFlow` (conflating) for status; text-publish semantics are
  "latest wins" which is what the UI already does.
- **Risk:** reading `StateFlow` in `MainActivity` without a lifecycle-aware
  collector leaks or misses events. **Mitigation:** use
  `collectAsStateWithLifecycle` in `AppRoot` and a `HiltViewModel`.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| JVM unit | `RecognizerStatusBusTest` | Turbine over `status` StateFlow; publish ordering. |
| JVM unit | `HomeViewModelTest` | `MainDispatcherRule` + Turbine; fake `RecognizerStatusBus`. |
| JVM unit | `VoiceEffectsImplTest` | `FakeSpeechRecognizer`, `FakeTextPublisher` (recording), `FakeAudioDucker`, in-memory `VoicePhraseRepository`. |

### Hand-off

PR 6 assumes:

- `TextPublisher` is wired to `RecognizerStatusBus`; the service never
  touches `Intent` for recognised-text events.

---

## PR 6 - `VoiceService` split + `FakeAudioSource` + pipeline tests

### Branch

`feature/voice-service-split` -> `develop`.

### Scope

New files:

- `service/pipeline/VoicePipeline.kt` - owns `AudioSource` +
  `SpeechRecognizer` + `VoiceCoordinator` wiring. Lifecycle: `start()`,
  `stop()`. Takes `CoroutineScope` from the service.
- `service/notification/VoiceNotificationController.kt` - owns channel
  creation, `createNotification`, `startForeground` / `notify`.
- `service/VoiceServiceController.kt` - thin
  `Service`/`AndroidEntryPoint` whose only job is lifecycle + intent
  routing; delegates to `VoicePipeline` + `VoiceNotificationController`.
- `service/AndroidAudioSource.kt` - implementation of `AudioSource`
  wrapping the existing `AudioRecorder` logic. Delete
  `service/AudioRecorder.kt` (its body moves here).

Edited files:

- `service/VoiceService.kt` - replaced by `VoiceServiceController.kt`
  (same registered name in `AndroidManifest.xml`; manifest unchanged).
- `service/BootReceiver.kt` - points at the new class name if it changed
  (otherwise untouched).
- `di/AppModule.kt` / `di/ServiceModule.kt` - bind `AudioSource` to
  `AndroidAudioSource`; `SpeechRecognizer` to `VoskSpeechRecognizer`
  (first production use of the PR 2 adapter).

### Public API delta

```kotlin
package ru.lsn03.voicemediacontroller.service.pipeline

class VoicePipeline(
    private val audio: AudioSource,
    private val recognizer: SpeechRecognizer,
    private val coordinator: VoiceCoordinator,
    private val grammarRepo: GrammarRepository,
    private val wakeRepo: WakeWordRepository,
    private val effects: VoiceEffects,
    private val publisher: TextPublisher,
    private val logger: AppLogger,
) {
    fun start(scope: CoroutineScope)
    fun stop()
}

package ru.lsn03.voicemediacontroller.service.notification

class VoiceNotificationController(
    private val context: Context,
    private val channelId: String,
) {
    fun ensureChannel()
    fun build(currentWake: String): Notification
    fun refresh(currentWake: String)
    companion object { const val NOTIF_ID = 1 }
}
```

### Exit criteria

- Build green; APK produced; on-device smoke: service starts on boot,
  shows notification, wake works, foreground-service microphone type still
  set.
- `VoiceServiceController.kt` is **under 120 LOC** (down from 253).
- Zero direct `AudioRecord` references outside `AndroidAudioSource.kt`.
- New `VoicePipelineTest` drives a `FakeAudioSource` emitting canned PCM
  and asserts the coordinator sees exactly one wake + one command for a
  scripted stream.

### Risk

- **Risk:** service lifecycle regression (foreground type bits, restart
  via `AlarmManager`). **Mitigation:** keep intent handling, alarm restart,
  and TTS wiring **unchanged** - only extract the pipeline/notification
  code paths; everything else stays byte-for-byte.
- **Risk:** `AndroidAudioSource` background thread leaks under test.
  **Mitigation:** `AudioSource.stop()` contractually joins the worker
  within 500 ms; add a Robolectric test for the real impl.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| JVM unit | `VoicePipelineTest` | `FakeAudioSource` (scripted PCM frames), `FakeSpeechRecognizer`, `FakeClock`, `FakeScheduler`. Asserts: wake fires exactly once; command executes; stop() stops audio. |
| Robolectric | `VoiceNotificationControllerTest` | shadow `NotificationManager`; verifies channel + title. |
| Robolectric | `AndroidAudioSourceTest` | assert `start -> stop -> start` sequence does not leak threads. |

### Hand-off

PR 7 assumes:

- `SpeechRecognizer` is the only speech seam used by `VoicePipeline`.
- Model-loading logic in `VoskSpeechRecognizer` (currently
  `VoskEngine.modelPath()` / `copyAssetFolder`) is cleanly isolated and
  can be replaced with a `ModelProvider` call.

---

## PR 7a - `ModelProvider` + locale-aware model storage

### Branch

`feature/model-provider` -> `develop`.

### Note

The original plan had this as one PR. With the "Voice models" settings
screen, download logic, and MockWebServer tests included, that PR
measurably exceeds the 400-LOC budget (estimate ~700 LOC). It is split in
two: **PR 7a** introduces `ModelProvider` + `SpeechLocale` and migrates
storage from `cacheDir` to `filesDir/models/<locale>/`; **PR 7b** adds
`ModelDownloader` + the settings screen.

### Scope

New files:

- `core/speech/SpeechLocale.kt` - typed locale (`ru`, `en`, `de`, ...).
- `core/speech/ModelProvider.kt` - resolves a `SpeechLocale` to a local
  model path, copying from assets if the asset exists for that locale.
- `core/speech/AssetModelProvider.kt` - default implementation.
- `core/speech/ModelLocation.kt` - `data class` with `path`, `locale`,
  `source` (ASSET / DOWNLOADED).

Edited files:

- `vosk/VoskEngine.kt` - replace `modelPath()` + `copyAssetFolder` with a
  constructor dependency on `ModelProvider`.
- `utils/Utilities.kt` - delete `MODEL_NAME` (grep check); the asset path
  is now derived from `SpeechLocale`.
- `di/AppModule.kt` - bind `ModelProvider`.
- `assets/vosk-model-small-ru/` moved to
  `assets/models/ru/vosk-model-small-ru/`. Asset tree reorg only; same
  files.

### Public API delta

```kotlin
package ru.lsn03.voicemediacontroller.core.speech

@JvmInline
value class SpeechLocale(val tag: String) {
    companion object {
        val RU: SpeechLocale
        val EN: SpeechLocale
    }
}

data class ModelLocation(
    val locale: SpeechLocale,
    val absolutePath: String,
    val source: Source,
) {
    enum class Source { ASSET, DOWNLOADED }
}

interface ModelProvider {
    suspend fun resolve(locale: SpeechLocale): Result<ModelLocation>
    fun isAvailable(locale: SpeechLocale): Boolean
}
```

### Exit criteria

- Build green; APK produced; manual smoke: wake-word still works on
  `ru` locale (default); storage now at
  `filesDir/models/ru/vosk-model-small-ru/`, not `cacheDir`.
- Old `cacheDir` model, if present, is cleaned up on first run.
- Zero references to `MODEL_NAME` (grep check).
- Coverage for `core.speech.*` **>= 85 %**.

### Risk

- **Risk:** APK grows if we accidentally ship both `ru` and `en` models in
  assets. **Mitigation:** PR 7a ships only `ru` in assets; `en` is
  download-only in PR 7b. Verify APK size delta in the PR.
- **Risk:** first-run migration from `cacheDir` is skipped by users whose
  cache was already evicted. **Mitigation:** acceptable - the asset copy
  path then runs as normal.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| Robolectric | `AssetModelProviderTest` | Robolectric `Context`; assert copy from assets to `filesDir/models/ru/...`. |
| JVM unit | `SpeechLocaleTest` | tag parsing, equality. |

### Hand-off

PR 7b assumes `ModelProvider.resolve` returns a `Result.failure` with a
distinguishable error type when the asset is missing (so the downloader
knows to kick in).

---

## PR 7b - `ModelDownloader` + "Voice models" settings screen

### Branch

`feature/model-downloader` -> `develop`.

### Scope

New files:

- `core/speech/ModelDownloader.kt` - streams a `.zip` of a Vosk model
  into `filesDir/models/<locale>/`, verifying SHA-256. Uses OkHttp (if we
  add it) or `HttpURLConnection` (zero new deps). Prefer the latter to
  keep the dep tree quiet.
- `core/speech/ModelCatalog.kt` - static map from `SpeechLocale` to
  download URL + checksum.
- `ui/screens/models/ModelsScreen.kt` - list of locales, download /
  delete / use.
- `ui/screens/models/ModelsViewModel.kt`.

Edited files:

- `core/speech/AssetModelProvider.kt` - becomes a composite:
  "asset first, then disk-downloaded, then fail with DownloadRequired".
- `ui/AppRoot.kt` - new route for the models screen.

### Public API delta

```kotlin
package ru.lsn03.voicemediacontroller.core.speech

sealed interface DownloadProgress {
    data object Idle : DownloadProgress
    data class Downloading(val bytes: Long, val total: Long?) : DownloadProgress
    data class Verifying(val sha: String) : DownloadProgress
    data object Done : DownloadProgress
    data class Failed(val reason: String) : DownloadProgress
}

interface ModelDownloader {
    fun download(locale: SpeechLocale): Flow<DownloadProgress>
    suspend fun delete(locale: SpeechLocale): Result<Unit>
}
```

### Exit criteria

- Build green; APK produced; manual: open "Voice models" screen; download
  `en` succeeds; switching locale uses the downloaded model.
- MockWebServer-backed tests cover: happy-path download, truncated
  stream, checksum mismatch, cancellation.
- Coverage for `core.speech.*` stays **>= 85 %**.

### Risk

- **Risk:** downloads on a foreground service thread drain battery.
  **Mitigation:** download runs in the ViewModel's scope, not the voice
  service.
- **Risk:** partial download leaves a corrupted directory. **Mitigation:**
  download to `filesDir/models/<locale>.part/`, verify, then atomic rename
  to `filesDir/models/<locale>/`.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| JVM unit | `ModelDownloaderTest` | `MockWebServer`; happy path, 404, truncated, bad checksum. |
| JVM unit | `ModelsViewModelTest` | `MainDispatcherRule`; fake `ModelDownloader`; Turbine over state. |

### Hand-off

PR 8 assumes `ModelCatalog` lists at least `ru` and `en`.

---

## PR 8 - Localisation of strings + per-locale phrase seeding

### Branch

`feature/i18n-strings` -> `develop`.

### Scope

Edited files:

- `app/src/main/res/values/strings.xml` - every user-facing Russian
  string currently embedded in Kotlin (`"Слушаю..."`,
  `"Слушаю команду..."`, `"Выполняю: $cmd"`, `"Нет разрешения на микрофон"`,
  `"🎤 Слушает $titleWake"`, `"Говори '$titleWake, следующий трек'"`,
  `"Жду команду"`, `"Нет текста"`) extracted to resources.
- `app/src/main/res/values-en/strings.xml` - new; English translations.
- `voice/repo/WakeWordRepository.kt` - `DEFAULT_WAKE_WORD_RU = "джарвис"`
  stays as a const; a new `defaultForLocale(SpeechLocale)` resolver added.
- `db/VoicePhraseEntity.kt` - add nullable `locale: String?` column; Room
  migration v1 -> v2.
- `db/AppDatabase.kt` - bump schema to 2; register a `Migration`.
- `db/DbSeeder.kt` - default phrases keyed by `SpeechLocale`.
- `voice/VoiceEffectsImpl.kt` + `voice/VoiceCoordinator.kt` - all literal
  strings go through a small `TextCatalog` (resource reader behind an
  interface) so the service can still format without a `Context` dep in
  pure classes.

### Public API delta

```kotlin
package ru.lsn03.voicemediacontroller.text

interface TextCatalog {
    fun get(key: TextKey, vararg args: Any): String
}

enum class TextKey {
    ListeningForWake,
    HeardPartial,
    HeardWakeTitle,
    ListeningForCommand,
    Executing,
    NoMicPermission,
    NotificationTitle,
    NotificationBody,
    IdleStatus,
}
```

### Exit criteria

- Build green; APK produced; device locale `en`: UI switches to English
  strings; wake-word default becomes `"jarvis"`; notification title
  translated.
- Room migration test asserts no data loss.
- All UI strings in Kotlin are routed via `TextCatalog` (grep check for
  hard-coded Cyrillic in `app/src/main/java/**/*.kt`).

### Risk

- **Risk:** users on upgrade keep their Russian wake word and phrases;
  switching device to English shouldn't silently wipe their customisation.
  **Mitigation:** migration tags all existing rows with `locale = "ru"`
  and keeps them active; a new locale starts with seeded defaults for its
  own tag; user's old phrases remain available if they switch back.

### Test matrix

| Level | Class | Fakes / fixtures |
|-------|-------|------------------|
| Robolectric | `AppDatabaseMigration1To2Test` | `MigrationTestHelper`; asserts rows get `locale = "ru"`. |
| JVM unit | `TextCatalogTest` | fake resource backing. |
| Robolectric | `WakeWordRepositoryLocaleTest` | asserts `defaultForLocale(RU) == "джарвис"`, `(EN) == "jarvis"`. |

### Hand-off

End of plan. After PR 8 merges, the next step (out of scope here) is a
`release/1.1` branch cut from `develop`.

---

## Appendix: cross-cutting invariants (every PR)

1. **No `.claude/`** in the diff; no `.claude` in `.gitignore`.
2. **Commit author**: `--author="Lvov Sergei <lvov.sn03@gmail.com>"`.
3. **No AI attribution** in commits or PR bodies.
4. **English commit subjects**, imperative mood, <= 72 chars, Conventional
   Commit prefix where it fits.
5. **PR size**: hard cap ~400 LOC of diff. If a PR unavoidably exceeds it
   (only PR 7 is at risk; it is already split into 7a/7b), split and
   mention the split in the PR description.
6. **Coverage ratchet**: every PR after PR 1 must raise or hold the global
   line-coverage number reported by JaCoCo.
7. **Characterization safety net**: PR 1's three characterization tests
   must stay green through all later PRs (updating assertions is permitted
   only when the PR consolidates duplicate logic; the PR description must
   call it out).
