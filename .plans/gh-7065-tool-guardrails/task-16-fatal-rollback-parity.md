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

- [x] Upstream status re-checked and recorded in PLAN.md.
- [x] Either the sync / `TokenStream` fatal tests pass, or the user chose how to ship the gap and the PR body / docs say so.
- [x] The Javadoc of `ToolGuardrailCompensationTest` about the fatal-rejection gap is removed or still accurate.


## Note from task 11 (2026-11-18)

The `TokenStream` mode has **no compensation at all**, with or without guardrails: with `compensateOnToolErrors(true)`, a throwing tool does not roll back an earlier one there, unlike sync, `CompletableFuture` and reactive (probe in task 11's Outcome). `ToolGuardrailCompensationTests` (task 11) skips `TOKEN_STREAM` for that reason. When [#7168](https://github.com/langchain4j/langchain4j/pull/7168) lands, check whether it changes this, then add `TOKEN_STREAM` back to that test's modes (remove the `@CompensatingModes` exclusion) and add the fatal-rejection tests for the sync and `TokenStream` modes.


## Outcome (2026-11-19; reviewed by the user, committed as the merge of `upstream/main`)

[#7168](https://github.com/langchain4j/langchain4j/pull/7168) merged on 2026-11-19 (`70dc8fec3`). **User decision:** bring it in with
**one merge commit** of `upstream/main` into the branch (not a 42-commit rebase, whose `ToolService` conflicts would have
come back in about 8 commits; the PR is squashed anyway, and the user had already merged `origin/main` once, `333744dee`).
A trial merge had 31 conflict hunks (18 in `ToolService`).

**How the two designs were combined:** the branch's position-based `ToolRound` / `ToolRoundCall` design stays; #7168's
behavior is carried over onto it:

- `ToolService`: `RolledBackTool` / `compensationError()`, the new rolled-back text and the "rollback failed" text,
  compensating-action errors added as suppressed to the error that fails the invocation, events fired also after a
  failed chat-memory rewrite, the results of an unsaved round left out of the rewrite, results matched in the chat
  memory by id + tool name + contents (newest first), `toVoidFuture` for `Mono` & co., a rejected tool start fails
  like a tool that threw. The guardrail rules stay: the reason comes from the failed step
  (`TOOL_INPUT_/OUTPUT_GUARDRAIL_FAILED`), and a result that a guardrail rejected keeps the guardrail's message
  verbatim (skipped in `rewriteCurrentResults` and `replaceCompensatedMessages`).
- Sync mode: `executeConcurrently` / `executeSequentially` return the error to rethrow; `executeTools` fails the round
  with the new `ToolRound#failBeforeRethrow(calls, error, accumulator, memory, messages)` (replaces
  `fireEventsOfFailedRound`), which fires the events, reports the calls that ran to cross-agent compensation, calls
  `onToolExecutionError`, and rolls back through the package-private `ToolService#failRoundBeforeRethrow(ToolRound,
  Outcome, ..)`. #7168's map-based public `failRoundBeforeRethrow(List, Map, Map, ..)` is replaced by it (new in
  1.25.0, `@Internal`, unreleased), because the maps cannot carry the guardrail stage or the original result of a tool
  whose output was rejected.
- `TokenStream`: #7168's claim / cancel / rollback logic in `AiServiceStreamingResponseHandler`, on the round: tools
  start through the new `ToolRound#executeTool(call, before, after, executor)` (records a rejected start as the call's
  failure), a fatal failure goes through `failBeforeRethrow`, and a cancellation settles the drained calls with
  `ToolRound#settleAsync` and rolls back with `ToolService#compensateOnCancellation(ToolRound.Outcome, unsaved, ..)`
  (replaces #7168's `compensateOnCancellation(Map, ..)`, same reason). Compensation when the error is sent to the LLM
  uses the new `compensateIfNeeded(ToolRound.Outcome, ..)`.
- Reactive: #7168's "register the tool before it starts" fix ported to the `StartedRound`; the cancel path now waits
  for the registered tools before it settles the round (they may not have started in the round yet).
  `compensateCollectedRoundAsync` takes the stream event emitter and the invocation error;
  `compensateOnCancellationAsync(ToolRound.Outcome, unsavedRequests, ..)` takes the unsaved requests.
- `CompensationReason` Javadoc and the docs (`tools.md`, `tool-guardrails.md`, `observability.md`,
  `non-blocking.md`) merged; the fatal-gap note in `tool-guardrails.md#compensation` is replaced: a fatal rejection
  rolls back in every mode.

**Tests:** `ToolGuardrailCompensationTest` (fatal tests now in sync and `CompletableFuture`, plus a same-round fatal test
and a failing compensating action attached as suppressed; 32 tests), `ToolCallbackFailureTest` (sync added; 8),
`ToolGuardrailCompensationTests` in `integration-tests-guardrails` (the `TOKEN_STREAM` exclusion is gone, the fatal
tests run in all four modes, plus a same-round fatal test). #7168's own `AiServiceToolCompensationTest`,
`ToolCompensationRollbackLoggingTest` and `ToolCompensatedEventTests` pass unchanged. Mutation-checked: skipping the
rollback in `failRoundBeforeRethrow` fails 9 tests (the sync variants).

**Cancel rule (user decision, 2026-11-19: keep ours, adapt the test).** #7168 tightened
`AiServiceStreamingPublisherTest#concurrent_cancellation_and_round_completion_never_double_compensate` to assert that
`credit` always runs once and is rolled back. Since task 08, `ToolRound` starts no tool once the invocation is
canceled, so when the cancel lands before `credit` has begun on its executor thread, `credit` never runs (probed:
`creditCount` 0 in the failing iterations; failed about 1 run in 4; `upstream/main` and the pre-merge branch pass).
The test now asserts that every `credit` that ran is rolled back exactly once (and never twice), and that the race
is exercised (`credit` runs in some iterations); it waits with a small polling helper instead of the latch. 10/10
runs pass; the compensation and publisher test classes pass 3/3 (269 tests).

**Spotless:** applied to the files this branch changes (also fixed the import order of
`ToolOutputGuardrailRequest`, left unsorted by an earlier commit). It also reformatted #7168's own files, because the
ratchet base during a merge is the old merge base; those were restored to upstream's version (not ours to reformat).

**Full runs:** `langchain4j` 2502 tests (before the test adaptation: only the race test failed), agentic 672 (incl.
`CrossAgentCompensationTest`), guardrail ITs 366 (`ToolGuardrailCompensationTests` 44); 0 failures.
