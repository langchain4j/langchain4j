# Task 20 (optional): Spring Boot, Quarkus and Logback Bump as Its Own PR

**Type:** Separate PR (integration test POMs only), branched from `upstream/main`

> **Why:** @dliubarskyi's `3fdea6601` restored `main`'s Spring Boot, Quarkus and logback versions in the
> `class-instance-loader` and `class-metadata-provider` integration test modules: they pass with them, so the bump can go
> in its own PR without dropping Spring Boot 3 coverage from PR #7202
> ([his comment](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7041051258)).
>
> **Optional:** ask the user whether the bump is still wanted before doing anything.

## Goal

If wanted, propose the version bump on its own, with the two fixes it needed (see the task 06a outcome in PLAN.md,
"Integration tests after the user bumped Quarkus (3.26.3 to 3.40.1.2) and Spring Boot (to 4.1.2)").

## What to Do

1. Ask the user whether to do it, and whether Spring Boot 3 coverage should be kept (for example a Spring Boot 3 and a
   Spring Boot 4 variant) rather than replaced.
2. Branch from `upstream/main` and reapply the bump from before `3fdea6601`: the Quarkus and Spring Boot versions in the
   module POMs, the `logback.version` pin aligned with the Boot BOM, and the empty `project.build.outputTimestamp` for
   the Quarkus module (with the comment in `integration-tests/pom.xml`).
3. Run the seven class loading / metadata integration test modules together.

## Done When

- [ ] The user decided whether the bump is wanted.
- [ ] If yes: a separate PR with the bump, all modules passing, and the PR text approved by the user before it is
      opened.
