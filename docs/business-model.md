# Business Model: Dive Operation Scheduling & Logistics Coordination Service

## Classification

- Repository: `cloud-itonami-isco-7541`
- ISCO-08: `7541`
- Occupation: Underwater Divers
- Social impact: diver-safety, worker-safety, public-safety

## Scope

**This actor coordinates dive-operation scheduling and logistics
only.** It never performs diving work itself, never finalizes a
dive-execution decision, never finalizes a dive-authorization
decision (approving a dive to proceed), and never overrides a dive
supervisor's or dive-safety-officer's judgment. Underwater divers
perform work where errors (decompression sickness, drowning,
equipment failure at depth) can cause death or serious injury —
categorically higher-stakes than ordinary workshop trades, comparable
to aviation-mechanic stakes — so every proposal this actor's advisor
can make is limited to coordination, not execution and not
authorization.

## Customer

- commercial diving contractors (inspection, construction, salvage)
- marine/offshore operators and dive-team crew leads

## Offer

- work-record logging (task, dive log, materials usage, progress)
- crew/dive-team schedule scheduling proposals
- safety-concern surfacing (equipment-condition, decompression-table,
  weather/current concern)
- dive-equipment/gas-supply order coordination

## Revenue

- monthly coordination-platform retainer
- per-dive-operation/per-site logistics fee

## Trust Controls

- no dive-execution decision (performing/completing the actual dive)
  is ever finalized by this actor
- no dive-authorization decision (approving a dive to proceed) is
  ever finalized by this actor
- no dive supervisor's or dive-safety-officer's judgment is ever
  overridden by this actor
- every safety-concern flag ALWAYS escalates to human sign-off, no
  exceptions, ever
- supply orders above the registered cost threshold always escalate
  to human sign-off
- dive-operation and diver provenance is independently verified
  before any coordination action
- coordination and audit records are auditable, not editable
