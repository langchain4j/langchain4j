# Task 09: Compensation Semantics for Guardrail Rejections

**Type:** Code Modification (`langchain4j-core` enum + `langchain4j` `ToolService`)

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: @dliubarskyi's rollback-before-rethrow PR (fatal parity).

> **D11 is confirmed** (option (a), refined by @dliubarskyi in
> https://github.com/langchain4j/langchain4j/discussions/7030#discussioncomment-19586042). The fatal-parity part
> (step 5) depends on @dliubarskyi's separate rollback-before-rethrow PR for the sync and `TokenStream` modes; its
> status is tracked in PLAN.md "Upstream Dependencies".

## Goal

Implement D11 so that compensation (`compensateOnToolErrors` and cross-agent `compensateOnError`) behaves
correctly when a tool guardrail rejects a call. Keep today's rule, **an error result the LLM sees = a failed
step**, and define it by the **final result** the LLM ends up with, not by the guardrail method that produced it.
Then guardrails need no special case in the failed-step logic. Only compensability and the reason need the
original result.

Two independent questions per tool:
- **Failed step?** Decided by the **guarded** (final) result: `isError == true` → failed step.
- **Compensable?** Decided by the **original** result: the tool ran **and** its original result was not an error.
  The `ToolExecution` handed to the compensating action carries the **original** result.

| Case | Rejected tool compensable? | Failed step (rolls back other compensable tools)? | What the LLM sees for the tool | `CompensationReason` |
|---|---|---|---|---|
| Input guardrail non-fatal failure | No (it never ran) | **Yes** | Guardrail message, verbatim | `TOOL_INPUT_GUARDRAIL_FAILED` |
| Output guardrail turns success → error (`failure(..)`, or a rewrite with `isError == true`) | **Yes**, with its original result, and it is **rolled back too** | **Yes** | Guardrail message / rewritten contents, verbatim (**not** the rolled-back template) | `TOOL_OUTPUT_GUARDRAIL_FAILED` |
| Output guardrail rewrite that keeps `isError == false` | Yes (normal success) | **No** (the no-rollback path for sanitizing guardrails) | Rewritten result | n/a |
| Original result already an error; output guardrail keeps it an error (rejects it, or rewrites it with `successWith(String)`, which keeps `isError`) | No (as today) | Yes (as today) | Guardrail message / rewritten contents, verbatim | `TOOL_EXECUTION_FAILED` |
| Original result an error; output guardrail flips it to `isError == false` (only possible with the contents rewrite `successWith(List<Content>, false)`) | No (it didn't succeed) | No (the LLM sees a success) | Rewritten result | n/a |
| Fatal (input or output) | Input: no. Output: yes, with its original result | Same as a thrown tool error (rolls back in all modes once @dliubarskyi's PR lands) | n/a (invocation aborts) | Guardrail constant (`TOOL_INPUT_GUARDRAIL_FAILED` / `TOOL_OUTPUT_GUARDRAIL_FAILED`) |

Other tools rolled back because of a guardrail rejection get the usual rolled-back text
("... rolled back due to failure of tool '<rejected tool>'").

## What to Do

0. The error-result rows, the fatal reason (guardrail constants) and the mixed-round rule (step 2), posted in
   https://github.com/langchain4j/langchain4j/discussions/7030#discussioncomment-19591223, were **confirmed** by
   @dliubarskyi in discussioncomment-19655223, with one point: `successWith(String)` must keep the original `isError`,
   so that sanitizing an error result never hides a failure. Task 03 already does this (only
   `successWith(List<Content>, boolean)` can change `isError`; pinned by
   `ToolGuardrailResultTest.output_text_rewrite_keeps_other_contents_and_error_flag`).
1. `langchain4j-core/.../observability/api/event/CompensationReason.java`:
   - add `TOOL_INPUT_GUARDRAIL_FAILED` and `TOOL_OUTPUT_GUARDRAIL_FAILED`, with Javadoc in the style of the existing
     constants (and `@since`)
   - **fix the Javadoc of `TOOL_EXECUTION_FAILED`**: it says "in the same tool-calling round", but compensable tools
     from earlier rounds of the invocation are rolled back too (and, with cross-agent compensation, tools of other
     agents). Word the new constants the same way.
2. **Per-invocation compensation** (`collectCompensable` ~1550, `compensateIfNeeded` ~1330,
   `compensateIfNeededAsync` ~1367 / ~1404), using the original-vs-guarded information from task 07/08:
   - failed = guarded result `isError()` (today's check; it now sees the guarded result, so no special case)
   - compensable = the tool ran and the **original** result was not an error. Add it to `compensableExecutions` with a
     `ToolExecution` carrying the real request and the **original** result, so the compensating action (e.g. one
     reading `toolExecution.result()` for a transaction ID) gets real data
   - reason from the failing tool: tool error → `TOOL_EXECUTION_FAILED`, input rejection →
     `TOOL_INPUT_GUARDRAIL_FAILED`, output success → error → `TOOL_OUTPUT_GUARDRAIL_FAILED`. Mixed rounds: the
     **first failing tool in request order** decides, consistent with today's `failedToolName` (confirm in step 0)
   - thread the reason through `rewriteCurrentResults`, `rewriteChatMemoryForCompensatedTools[Async]`,
     `replaceCompensatedMessages` / `rolledBackResultMessage` (~1597-1666), and `fireCompensatedEvents`. Keep the
     reactive path's `alreadyCollected` / `preCollectedFailedToolName` variant consistent.
   - **do not overwrite the rejected tool's message.** `rewriteCurrentResults` and `replaceCompensatedMessages`
     currently replace every compensated tool's message with the rolled-back template; the output-rejected tool must
     keep the guardrail's verbatim message.
3. **Cross-agent compensation** (`processToolResults` ~1100-1145):
   - report an output-rejected tool through `onCompensableToolExecution` (today guarded by `!result.isError()`, which
     must now look at the **original** result), with the original result in the `ToolExecution`
   - a guardrail rejection makes the guarded result an error, so `anyToolErrored` is set and `onToolExecutionError`
     fires naturally
4. **Chains:** since the rule looks only at the final guarded result, a chain where one guardrail flips to an error
   and a later one rewrites back to `isError == false` is not a failed step. Test it.
5. **Fatal:** a fatal guardrail exception follows the same path as a thrown tool error in every mode
   (`CompletableFuture`: `firstError` → compensate → fail the future; sync and `TokenStream`: roll back, then
   rethrow / `onError`, **once @dliubarskyi's PR lands**). An output-fatal tool did run, so it is compensable with its
   original result. Do not reimplement the sync / `TokenStream` rollback here; if his PR hasn't merged yet, ask the
   user (wait, rebase onto his branch, or ship with async-only fatal tests and a follow-up).
6. Tests (sync, `CompletableFuture`, `TokenStream`; per-invocation and cross-agent):
   - every row of the table, including `failure(..)` **and** a rewrite with `isError == true` for the output case
   - the compensating action of an output-rejected tool receives the original result
   - the rejected tool's message stays verbatim while the others get the rolled-back text, both in chat memory and in
     the next request
   - a rewrite keeping `isError == false` does not trigger rollback; the flip-back chain from step 4
   - an **error** result sanitized with `successWith(String)` (e.g. `"[content removed]"`) stays an error: still a
     failed step, the other compensable tools are rolled back with `TOOL_EXECUTION_FAILED`, and the LLM sees the
     sanitized text (@dliubarskyi, discussioncomment-19655223)
   - mixed rounds (tool error + guardrail rejection, both orders) pin the reason rule
   - rollback **across rounds** (a compensable tool from round 1, a rejection in round 2); `ToolCompensatedEvent.reason()`
   - fatal input / output rejection with a compensable tool in an earlier round, in all three modes (mirror
     @dliubarskyi's `credit` / `withdraw` repro)
   - an agentic system with `compensateOnError(true)`: an output rejection rolls back the hierarchy, including the
     rejected tool and tools that other agents ran before

## Files/Areas

- `langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/CompensationReason.java`
- `langchain4j/src/main/java/dev/langchain4j/service/tool/ToolService.java` (`processToolResults`,
  `collectCompensable`, `collectCompensableRound`, `compensateIfNeeded*`, `rewriteCurrentResults`,
  `rewriteChatMemoryForCompensatedTools*`, `replaceCompensatedMessages`, `rolledBackResultMessage`,
  `fireCompensatedEvents`)
- `AiServiceStreamingEventPublisher.java` (reactive pre-collection path)
- `langchain4j-agentic/src/main/java/dev/langchain4j/agentic/agent/AgentInvocationHandler.java` (cross-agent hooks;
  should need no change; verify)
- Existing compensation tests: `grep -rln compensat langchain4j/src/test langchain4j-agentic/src/test`

## Key Points

- Several `compensate*` methods are `public` with `@since 1.20.0`. **Do not change public signatures**; add
  overloads or derive the reason internally.
- New enum constants can break exhaustive `switch`es in user code (`CompensationReason` is `@Experimental`); note
  this for task 12 and the PR body.
- Compensation off (`compensateOnToolErrors == false`, no cross-agent compensation) must be completely unaffected.
- Docs (task 13) must explain that a failure (`failure(..)` or a rewrite with `isError == true`) is a failed step
  that rolls back **every compensable tool of the whole invocation** (and, cross-agent, of agents that ran before),
  while a rewrite keeping `isError == false` sanitizes without rollback.

## Done When

- [x] D11 confirmed by the maintainers.
- [x] No objections to discussioncomment-19591223 (confirmed in discussioncomment-19655223).
- [x] Both new `CompensationReason` constants exist with Javadoc; `TOOL_EXECUTION_FAILED` Javadoc corrected.
- [x] Every table row holds for all modes and for cross-agent compensation (tests).
- [x] The failed-step decision uses only the guarded result; compensability and the compensating action use the
      original result.
- [x] The output-rejected tool keeps its verbatim guardrail message.
- [x] Fatal rejections roll back in all modes (`CompletableFuture` mode; sync / `TokenStream` deferred to task 16 by the user's decision, 2026-11-17).
- [x] No public signature changes; behavior with compensation off is unchanged.
