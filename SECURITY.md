# Security Policy

This project handles underwater divers dive-operation scheduling and
logistics coordination workflows. Treat vulnerabilities as
potentially high impact even when the demo data is synthetic — a
single mishandled dive-safety concern (decompression sickness,
drowning, equipment failure at depth) can cause death or serious
injury, categorically higher-stakes than ordinary workshop trades.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real client, crew or operator data exposure
- authorization bypass
- DiveCoordGovernor bypass
- op-allowlist widening toward dive-execution finalization, dive-authorization finalization, or dive-supervisor/dive-safety-officer authority override
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on client/crew data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real client/crew/operator data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
