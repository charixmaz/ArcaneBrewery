# Foundation V1.16: deterministic instability and machine failures

V1.16 turns the V1.15 instability preview into persistent gameplay consequences.

## Identity invariant

The core rule remains unchanged:

**RNG determines variation, not drink identity.**

V1.16 does not use a random roll to decide what recipe a batch becomes.

Failure outcome is derived deterministically from:
- the locked alchemical additive plan;
- additive load;
- catalyst diversity;
- persisted machine integrity;
- persisted machine contamination.

Machine condition is a batch/machine condition and is never written into `ProcessProfile` or `DrinkFingerprint`.

## Machine condition

Every alchemy machine snapshot now contains:
- integrity, 0–100;
- contamination, 0–100.

New machines start at 100 integrity and 0 contamination.

Successful and failed infusions can wear the machine. The condition is persisted atomically with the machine inventory and operation state.

V1.15 ABAM V2 snapshots remain readable and migrate as pristine machines. New writes use ABAM V3.

## Failure outcomes

Initial deterministic outcome bands:
- `STABLE_SUCCESS`
- `UNSTABLE_SUCCESS`
- `FAILED_INFUSION`
- `CRITICAL_FAILURE`

Stable and unstable success both produce the exact prepared additive plan captured when processing started.

A failed infusion:
- consumes one selected catalyst from each catalyst slot;
- leaves the fermented base unprepared;
- marks presentation lore with failed-reaction residue;
- damages/contaminates the machine.

A critical failure:
- consumes the selected catalysts;
- destroys the fermented base;
- heavily damages/contaminates the machine;
- creates a controlled entity-damaging explosion with block breaking disabled.

Failures do not produce an alternate recipe or fingerprint.

## Persistence and restart behavior

Outcome is recomputed deterministically from the locked operation and the persisted pre-completion machine condition.

The resulting item state and updated machine condition are committed in one machine snapshot. A successful persistence update happens before GUI state is replaced.

Failed and critical operations clear the completed operation after their result is committed. Successful operations retain the completed operation for the existing V1.15 output validation boundary.

## Player feedback

The start control shows:
- machine integrity;
- machine contamination.

Starting an infusion reports the current deterministic instability estimate.

Completion feedback distinguishes stable success, unstable success, failed infusion and critical failure through messages, particles/sounds and the controlled critical explosion.

## Compatibility

- V1.15 snapshot V2 remains readable.
- Existing prepared bottle metadata is unchanged.
- Existing recipe fingerprints are unchanged.
- Machine condition does not enter recipe identity.

## Current maintenance boundary

V1.16 introduces wear but not a dedicated repair GUI. An emptied machine core can be replaced to reset the physical machine. A future maintenance/economy milestone can introduce explicit servicing materials and costs.

## Verification focus

Tests cover:
- deterministic outcome selection;
- condition-driven instability;
- bounded machine wear;
- failed infusion catalyst consumption;
- critical base destruction;
- no preparation metadata on failed output;
- V3 condition round-trip;
- V2 snapshot compatibility;
- atomic condition persistence;
- malformed/corrupt snapshot rejection inherited from V1.15.

Live Paper validation should additionally cover critical explosion damage, multi-viewer feedback, repeated machine wear, and restart immediately before/after a failure completion.
