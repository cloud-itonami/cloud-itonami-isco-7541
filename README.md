# cloud-itonami-isco-7541

Open Occupation Blueprint for **ISCO-08 7541**: Underwater Divers.

This repository designs a forkable OSS business for an underwater-diving-operations coordination service: a dive-operation scheduling/logistics coordination robot manages work-record logging, crew/dive-team scheduling, safety-concern flagging and dive-equipment/gas-supply order coordination under a governor-gated actor, so the diving operator keeps its own operating records instead of renting a closed dive-scheduling SaaS.

**This actor coordinates DIVE-OPERATION SCHEDULING/LOGISTICS ONLY — it never performs diving work itself and never makes a dive-authorization decision.** Underwater divers perform work where errors (decompression sickness, drowning, equipment failure at depth) can cause death or serious injury — categorically higher-stakes than ordinary workshop trades, comparable to aviation-mechanic stakes. The actor's closed op-allowlist contains no op that directly finalizes a dive-authorization decision (approving a dive to proceed) or a dive-execution decision, nor overrides a dive supervisor's/dive-safety-officer's judgment. Any proposal that attempts any of these is a hard, permanent block, never overridable by human approval, and NEVER auto-commit-eligible under any confidence level.

**Maturity: `:implemented`.** `src/divecoord/` implements the
`DiveCoordActor` as a `langgraph.graph/state-graph`
(`divecoord.actor`) wired to a `Dive Operation Scheduling/Logistics
Coordination Advisor` (`divecoord.advisor`) and an independent
`DiveCoordGovernor` (`divecoord.governor`), following the itonami
actor pattern (ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok? true) +-> :request-approval (:escalate? true, human-in-the-loop
interrupt) +-> :hold (:hard? true)`. See `kbb -M:test` output for
the current test/assertion counts.

HARD invariants (always `:hold`, never overridable): the
dive-operation/dive-team record must be independently
verified/registered before any action; a referenced diver must be a
registered certified crew member belonging to that dive-operation;
`:effect` must be `:propose` only (no hardware dispatch, no diving
work performed); the closed op-allowlist is enforced (no op in the
allowlist finalizes a dive-authorization decision, finalizes a
dive-execution decision, or overrides dive-supervisor/dive-safety-
officer authority); and any proposal that attempts to directly
finalize a dive-authorization decision (approving a dive to
proceed), finalize a dive-execution decision, or override a dive
supervisor's/dive-safety-officer's judgment is a hard, **permanent**
block — detected as finalization/execution action phrases (never
bare nouns like "dive"/"depth"/"decompression", which are ordinary
vocabulary for this domain and must not false-trip the guard).

Always-escalate ops (human sign-off regardless of confidence, mapping
this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (every surfaced equipment-condition/
decompression-table/weather-current concern, ALWAYS, no exceptions,
ever) and `:coordinate-supply-order` above the registered cost
threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a dive-operation scheduling/logistics coordination robot performs work-record logging, crew/dive-team schedule proposals, safety-concern surfacing and dive-equipment/gas-supply order coordination under an actor that proposes
actions and an independent **Dive Operation Scheduling/Logistics Coordination Governor** that gates them. The governor never
dispatches hardware itself, never performs diving work, never finalizes a dive-authorization decision, and never overrides a dive supervisor's/dive-safety-officer's judgment; `:high`/`:safety-critical` actions (such as a safety-concern flag or an above-threshold supply order) require human sign-off.

## Core Contract

```text
dive-operation roster + diver roster + dive schedule
        |
        v
Dive Operation Scheduling/Logistics Coordination Advisor -> DiveCoordGovernor -> log record/schedule/order, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a dive-execution decision, finalize a dive-authorization
decision (approve a dive to proceed), override a dive supervisor's/
dive-safety-officer's judgment, suppress an operating record, or
disclose sensitive data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `7541`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
