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

- [x] The user decided whether the bump is wanted (yes: the session goal, 2026-11-20).
- [x] If yes: a separate PR with the bump, all modules passing, and the PR text approved by the user before it is
      opened.

## Outcome (2026-11-20): Implemented, UNCOMMITTED, Awaiting the User's Review

The user set "implement task 20" as the session goal, so the bump is wanted. Nothing is committed or pushed, and the PR
is not open.

- **Branch:** `bump-class-loading-it-versions` off `upstream/main` (`a30ea36b4`), in a separate worktree.
- **Change (3 POMs, the reverse of `3fdea6601` for them):**
  - `integration-tests-class-instance-loader-spring` and `integration-tests-class-metadata-provider-spring`:
    `spring-boot-dependencies` 3.5.5 to 4.1.2, `spring-boot-maven-plugin` 3.4.5 to 4.1.2 (now matches the BOM, as the
    `renovate.json5` comment on integration test plugins expects), `logback.version` 1.5.16 to 1.5.38.
  - `integration-tests-class-instance-loader-quarkus`: Quarkus 3.26.3 to 3.40.1.2, plus the empty
    `project.build.outputTimestamp` with its comment. `integration-tests/pom.xml` needs no change on `main`: its skip
    properties already exist (`3fdea6601` only moved them back to `main`'s place).
- **Versions chosen:** Spring Boot 4.1.3 and 4.2.0 came out on 2026-11-19 (4.2.0 moves to logback 1.6.6). Kept 4.1.2,
  the version tested on 2026-11-16, because `renovate.json5` waits 7 days before it takes a new release
  (`minimumReleaseAge`). Quarkus 3.40.1.2 (2026-11-09) is still the newest 3.x release.
- **Spring Boot 3 coverage (open question for the user, default: replace):** these modules move to Boot 4. Boot 3.5.5
  is still covered by `integration-tests-test-assertions-spring` and `-spring-starter`, and Quarkus 3.26.3 by
  `integration-tests-test-assertions-quarkus`, which this PR leaves alone. The PR text offers Boot 4 variants next to
  the Boot 3 modules instead, if the reviewer prefers that.
- **Validation:** `./mvnw verify -am -Drevapi.skip -pl <the six modules>` in the worktree is BUILD SUCCESS: class
  instance loader 6, its Spring app 3, its Quarkus app 3, class metadata provider 2, its Spring app 2, guardrails 116,
  and through `-am` core 2037 and `langchain4j` 2295 (+597 ITs), all 0 failures. `dependency:list` confirms
  `spring-boot` 4.1.2 and `logback-classic` / `logback-core` 1.5.38; the log shows Quarkus 3.40.1.2. Nothing was
  installed into `~/.m2` (reactor build). There were six modules, not seven, because `string-keys` belongs to #7214
  and is not on `main`.
- **Docs:** no documentation or workflow mentions these versions (searched `*.md`, `*.mdx`, `.github`).
- **PR text:** `.plans/gh-7065-tool-guardrails/pr-body-it-version-bump.md` (never commit it), to be approved by the
  user before the PR is opened.
- **Left:** user review, commit (subject without fix/close/resolve words, `-s`, `Assisted-By`), push to the fork, open
  the PR after the user approves the text, then link it from #7202 if wanted.

**Opened (2026-11-20):** the user approved; commit `c5530f5a6` on `bump-class-loading-it-versions`, pushed to the fork,
PR [#7216](https://github.com/langchain4j/langchain4j/pull/7216) with the text from `pr-body-it-version-bump.md`.
