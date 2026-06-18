# 08 — M8: Documentation & Final Pass

> **Goal:** make the project reproducible from scratch and confirm the whole thing builds, tests,
> and deploys end to end.
>
> **Prerequisites:** M1–M7 complete. Read [`00-overview.md`](00-overview.md).

## Build

### `README.md` (already scaffolded — finalize against the real build)
Ensure it documents:
- env vars (`JBOSS_SERVER_LOG_DIR`, `JBOSS_APP_LOG_DIR`) and resolution order;
- build: `mvn clean package` → `jboss-log-viewer-ear/target/jboss-log-viewer.ear`;
- deploy: copy the EAR to `EAP_HOME/standalone/deployments/`;
- URLs (UI + the three API endpoints);
- the M1/M6/M7 validation commands assembled into a **reproducible smoke-test script**;
- the browser checklist from M7.

### Final pass
- Confirm no stray dependencies (only Commons Compress as a third-party runtime dep).
- Confirm SLF4J is `provided`, EE API is `provided`.
- Confirm no `innerHTML` on log content, all paths go through `PathSecurity`.

## Validate

- Follow the README from scratch on a clean WildFly/EAP — deployment works.
- `mvn clean verify` — full build with all tests green.
- Run the end-to-end smoke test (API `curl`s + browser checklist).

## Gate

README reproduces a working deployment; `mvn clean verify` is green; smoke test passes.

## Notes

- This milestone is also the place to reconcile any drift between the code and
  `implementation-plan.md` — update the plan if the implementation diverged for a good reason.
