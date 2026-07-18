# Governance

`cloud-itonami-isco-7541` is an OSS open-occupation blueprint. Governance covers
both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Advisor cannot directly dispatch robot actions, perform diving work, or disclose records.
- DiveCoordGovernor remains independent of the advisor.
- hard policy violations cannot be overridden by human approval — this includes
  any proposal to finalize a dive-execution decision, finalize a
  dive-authorization decision (approve a dive to proceed), or
  override a dive supervisor's or dive-safety-officer's judgment.
- every commit, hold and approval path is auditable.
- real client/crew/operator data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and data-flow
review.

Certified operators can lose certification for:

- bypassing policy checks
- mishandling client/crew/operator data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
- widening the op-allowlist toward dive-execution finalization, dive-authorization finalization, or dive-supervisor/dive-safety-officer authority override
