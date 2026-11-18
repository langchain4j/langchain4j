# Task 14: Final Build, Spotless, and PR Preparation

**Type:** Build / Release preparation

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: all of them; list their final status in the PR body.

## Goal

Verify the whole change builds and passes across all affected modules, then prepare (but do **not**
commit or push without approval) the single tool guardrails PR.

## What to Do

1. Build and test the affected modules:
   - `mvn -pl langchain4j-core,langchain4j,langchain4j-mcp,langchain4j-agentic -am install` (use `-Djacoco.skip=true` only to
     unblock local installs; the jacoco gate must pass for the new code in a normal run)
   - `mvn -pl integration-tests/integration-tests-guardrails test`
   - relevant MCP ITs from task 10
   - `langchain4j-guardrails` tests, which depend on core guardrail types (regression)
   - revapi (task 12)
2. **`mvn -Pspotless validate` at the repo root.** This is exactly what CI runs; per-module checks have missed
   import-order violations before.
3. Run a final review with the `code-review` agent over the full diff, and address any high-confidence findings.
   Include a D20 check: no guardrail mechanics outside the guardrail API (`ToolService`, the streaming classes,
   `DefaultAiServices`, MCP, agentic only call it).
4. Read the PR template (`.github/pull_request_template.md` / `.github/PULL_REQUEST_TEMPLATE/`) and draft the PR
   body using it, including:
   - summary + link to the spike and the discussion decision record (D1–D20)
   - **the highest design guideline, up front (D20):** the tool guardrails are kept as much of a black box as
     possible. Everything guardrail-specific is behind the public guardrail API
     (`ToolGuardrailService#executeGuardrails` / `executeGuardrailsAsync`, mirroring
     `GuardrailService#executeGuardrails`), so `ToolService` and downstream frameworks (Quarkus,
     [quarkiverse/quarkus-langchain4j#3162](https://github.com/quarkiverse/quarkus-langchain4j/issues/3162); CDI; Micronaut) only call it and use what it returns, without knowing the
     guardrail internals. Explain why the public API is shaped as it is (`GuardedToolExecution` exists only for the
     rejected-after-rewrite request) and what stays caller-side (the loop's history and event bookkeeping, like
     `DefaultAiServices` with the guarded user message)
   - behavior notes: verbatim failure message, runs on error results, resolution order (builder → class → method, all run; differs from AI service guardrails), compensation reasons
   - compatibility summary from task 12 (sealed permits, new enum constants → exhaustive `switch` caveat)
   - follow-ups: prompt-injection tool guardrail and injection-side tests (@PSchmitz-Valckenberg), Quarkus deprecation
     ([quarkiverse/quarkus-langchain4j#3162](https://github.com/quarkiverse/quarkus-langchain4j/issues/3162)), opt-in idempotent retry, possible stop-on-first-failure, a per-tool opt-out from global / class guardrails (D6, if users ask), per-MCP-server guardrails on `McpToolProvider.builder()` (and whether it absorbs [#5345](https://github.com/langchain4j/langchain4j/pull/5345),
     @jmartisk)
   - related PRs: [#7088](https://github.com/langchain4j/langchain4j/pull/7088) (precedence Javadoc; it replaced our [#7109](https://github.com/langchain4j/langchain4j/pull/7109) from task 01), @dliubarskyi's sync / `TokenStream` rollback-before-rethrow PR (fatal parity),
     and [#7106](https://github.com/langchain4j/langchain4j/pull/7106) if it affected the async wiring
   - exact test commands run, with results
   - `Fixes [#7065](https://github.com/langchain4j/langchain4j/issues/7065)` in the body only
5. Draft the commit message: the first line must not contain close(s/d) / fix(es/ed) / resolve(s/d), and
   `Fixes [#7065](https://github.com/langchain4j/langchain4j/issues/7065)` goes at the end of the body.
6. @PSchmitz-Valckenberg's injection tests are a follow-up (already settled with him). Once the PR is open, offer
   the user a short discussion comment pointing him to it; never post without approval.
7. **Stop and wait for the user's review and approval before committing, pushing, or opening the PR.**

## Files/Areas

- The whole diff; `.github/pull_request_template.md`

## Key Points

- Never commit before the user approves.
- The Javadoc precedence fix (task 01) landed through [#7088](https://github.com/langchain4j/langchain4j/pull/7088), not our [#7109](https://github.com/langchain4j/langchain4j/pull/7109). Reference [#7088](https://github.com/langchain4j/langchain4j/pull/7088) from this PR.

## Done When

- [ ] All builds, tests, revapi and root `spotless validate` pass (commands and results recorded in PLAN.md).
- [ ] Code review done; findings addressed or explicitly deferred with the user.
- [ ] PR body (template-compliant) and commit message drafted and shown to the user.
- [ ] User approval received before any commit or push.

## Commit structure notes (added 2026-11-16, from task 06a)

- **Make renames visible.** `DefaultGuardrailService` became `DefaultAiServiceGuardrailService` (and its test
  `DefaultGuardrailServiceTests` became `DefaultAiServiceGuardrailServiceTests`) while the content changed a lot, so git
  reports delete + add (similarity under 50%). Commit the rename in its own commit first (a rename-only change that updates
  the references and keeps the old content, with a working build), then the refactoring on top, so history and the PR
  diff show a rename. Ask the user how they want 06a split before committing anything.
- The `.tasks/` folder and the task 05 import-order fixes in `langchain4j-core` (four files) need to be placed in the
  right commits too (the fixes belong with task 05's files).

## Post-PR step: comment on quarkus-langchain4j#3162 (user approved 2026-11-18)

Once the PR is open, post the `resolveMethodKey` item on [quarkiverse/quarkus-langchain4j#3162](https://github.com/quarkiverse/quarkus-langchain4j/issues/3162),
linking the PR. Draft: `~/.hermes/cache/scratch/issue-3162-comment.md` (fill in the PR link first). Show the user the final text before posting if
anything changed. Do not post before the PR exists.
