# Task 16: Fatal-Rejection Rollback Parity (circle back)

**Type:** Verification / Code Modification (`langchain4j`, tests), run just before task 14

> **Why this task exists (user decision, 2026-11-17):** task 09 shipped with fatal-rejection rollback tested in the
> `CompletableFuture` mode only, because @dliubarskyi's "rollback-before-rethrow" PR (sync and `TokenStream` modes roll
> back compensable tools before rethrowing a tool error) was not open yet. Do not reimplement it here without asking.

## Goal

Close the fatal-parity gap of D11 / task 09 step 5, or decide with the user how to ship it.

## Update 2026-11-18

The upstream change is **[#7168](https://github.com/langchain4j/langchain4j/pull/7168)** (@dliubarskyi, open). Its body says it relates to [#7065](https://github.com/langchain4j/langchain4j/issues/7065) and that "a fatal tool guardrail
rejection will fail the invocation the same way as a rethrown tool error". It rewrites the same compensation code as
task 09, so whichever lands second must be reconciled: `ToolService` (`collectCompensable*`, `compensateIfNeeded*`,
`rewriteCurrentResults`, `replaceCompensatedMessages`, `rolledBackResultMessage`), `CompensationReason`,
`AiServiceStreamingEventPublisher`, `AiServiceStreamingResponseHandler`, `AiServiceTokenStream`, and
`CrossAgentCompensationTest`. Things to carry over: the new rolled-back text (the verbatim-message rule for the guardrail
rejected tool must survive it), `ToolCompensatedEvent#compensationError()` (the builder now requires it), and the
rollback of a compensable tool whose output was rejected in the sync / `TokenStream` fatal paths.

## Steps

1. `git fetch upstream`; check whether the rollback-before-rethrow change exists or has merged
   (`gh pr list -R langchain4j/langchain4j --author dliubarskyi --state all`, discussion [#7030](https://github.com/langchain4j/langchain4j/discussions/7030)). Update the
   "Upstream Dependencies" row in PLAN.md.
2. **If it landed:** rebase onto `upstream/main`; add the sync and `TokenStream` fatal input / output rejection tests
   (a compensable tool in an earlier round, mirror @dliubarskyi's `credit` / `withdraw` repro); update the class Javadoc of
   `ToolGuardrailCompensationTest`, which says fatal rejections are covered in the `CompletableFuture` mode only.
3. **If it has not landed** when the PR is about to open: ask the user to choose between (a) shipping with the
   documented gap (PR body + docs note: a fatal guardrail rejection rolls back compensable tools only in the
   `CompletableFuture` mode), (b) implementing the sync / `TokenStream` rollback here, (c) waiting. Asking
   @dliubarskyi about it is the user's call.
4. Update task 13 docs and the PR body to match.

## Done When

- [ ] Upstream status re-checked and recorded in PLAN.md.
- [ ] Either the sync / `TokenStream` fatal tests pass, or the user chose how to ship the gap and the PR body / docs say so.
- [ ] The Javadoc of `ToolGuardrailCompensationTest` about the fatal-rejection gap is removed or still accurate.


## Note from task 11 (2026-11-18)

The `TokenStream` mode has **no compensation at all**, with or without guardrails: with `compensateOnToolErrors(true)`, a throwing tool does not roll back an earlier one there, unlike sync, `CompletableFuture` and reactive (probe in task 11's Outcome). `ToolGuardrailCompensationTests` (task 11) skips `TOKEN_STREAM` for that reason. When [#7168](https://github.com/langchain4j/langchain4j/pull/7168) lands, check whether it changes this, then add `TOKEN_STREAM` back to that test's modes (remove the `@CompensatingModes` exclusion) and add the fatal-rejection tests for the sync and `TokenStream` modes.
