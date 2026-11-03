# Validation: rep segmentation gating

Date: 2026-11-03
Status: complete

## Scope

Harden `core` rep segmentation against noisy plate tracks: gate candidate reps by duration,
displacement and acceleration turnaround, and merge only noise-like adjacent fragments.
No app/UI files touched.

## Changes

`core/src/main/kotlin/com/vbtdroid/core/phases/PhaseSegmenter.kt`

- `PhaseConfig` already carried `minRepSec = 0.45`, `minRomM = 0.10`,
  `minConcentricSec = 0.15`, `mergePauseSec = 0.25`; all four are validated as finite and
  non-negative in `segmentDetailed`.
- `validRep` now also requires turnaround evidence:
  - vertical acceleration (`ayMps2`) sign change within ±2 samples of the concentric velocity
    peak, or
  - a vertical velocity sign change across the rep (zero samples are skipped, so a pause at the
    bottom does not hide the reversal), or
  - a clear deceleration from the concentric velocity peak with at least half the minimum ROM
    travelled afterwards (covers degraded/acceleration-free tracks and deadlift lockouts).
- Existing gates kept: rep duration, concentric duration, and ROM (max y − min y over the rep's
  `MotionSample`s).
- New `mergeNoiseFragments`: merges adjacent candidates only when the gap is `< mergePauseSec`,
  contains at least two samples with less than `minRomM / 2` of travel, and the earlier rep does
  not end in a lockout. A real top pause therefore never collapses two reps; genuinely separate
  cycles are preserved.

Phase types, `Phase`, `RepSlice`, `PhaseSegmentation` and metric semantics are unchanged.

## Tests

`core/src/test/kotlin/com/vbtdroid/core/phases/PhaseSegmenterTest.kt` adds:

- `noisy signal with jitter and velocity bumps keeps the true rep count` (2 true reps, ±0.01 m
  jitter, ±0.06–0.07 m/s bumps → exactly 2).
- `stationary jitter alone does not create a rep`.
- `monotonic velocity without a turnaround is rejected`.
- Existing coverage retained: 3 continuous squat reps without top pauses, deadlift without
  lockout, too-short and too-small-ROM fragments, 0.10 s and 0.12 s lockout cases.

Command:

```bash
./gradlew :core:test --no-daemon
```

Result: BUILD SUCCESSFUL. 71 tests, 0 failures. `PhaseSegmenterTest`: 20 tests, 0 failures.

## Concerns

- Candidate merging is intentionally conservative. Fragments separated by an exact zero gap or by
  a lockout are not merged; the duration/ROM/concentric thresholds remain the primary noise gate.
- Real sensor data was not available in this environment; tests use synthetic `MotionSample`
  curves. Validation on recorded noisy sets is still recommended.
