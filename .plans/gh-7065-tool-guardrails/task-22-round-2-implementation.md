# Task 22: Implement the Round 2 Decisions

**Type:** Code, one step at a time

> **Why:** @dliubarskyi approved our answers to the round 2 design questions on 2026-11-25 (see
> [task 21](task-21-design-questions-round-2.md), "Dmytro's Answer"), plus one addition to item 2 (a note in the
> tool result after an input rewrite).
>
> **How (user, 2026-11-25):** implement every step, without committing anything, then stop for one review of the
> whole round (this replaced an earlier one-step-at-a-time plan). The decisions and their details are in the task 21 notes: read the
> notes of the step's items before starting it.

## Sync

- 2026-11-25: upstream/main merged into the branch (`cef091c86`, clean), pushed. 59 ahead / 0 behind.
- Upstream PR #7297 (@dliubarskyi, open, "Keep chat memory valid when a tool error ends an AI Service call", issue
  #7255) rewrites the memory handling of a failed round in `ToolService` (+211/-51) and
  `AiServiceStreamingEventPublisher`. It will conflict with step 1 when it merges; resolve with a merge commit, like
  #7168.

## Steps

Ordered by dependencies: item 2 simplifies `ToolRound` first; item 6 needs item 7's source inference and item 1's
class rule; the rewrite note builds on item 2 and waits for Dmytro's answer on its details.

| Step | Items | Content | Status |
|---|---|---|---|
| 1 | 2, 15 | Memory keeps the model's original call: `ToolRound` stops touching the history (`updateHistory*`, `replace`, the memory / messages parameters of `complete*`); revert the NOTE on `ChatMemory.set` / `setAsync` to compensation only; memory-rewrite tests replaced by one per mode asserting the original call; docs. Delete both thinking ITs. | done |
| 2 | 16 | `successWithArguments(Map<String, ?>)` on `ToolInputGuardrail` and `ToolInputGuardrailResult`; docs row + example; test helper moved | done |
| 3 | 4 | `IMMEDIATE` + output rewrite: `String` gets the guarded text, other types fail fast, internal "object dropped" flag | done |
| 4 | 11 | `afterToolExecution` throws: same "error wins" result for the event and the rollback, all 4 modes; Breaking Changes entry | done |
| 5 | 10 | Compensation events carry the guarded result, the action keeps the raw one; data-exposure warning in the docs and Javadoc | done |
| 6 | 5 | Cancellation: a call that hasn't started doesn't start (`CompletableFuture`, `Flow.Publisher`); `tools.md`; Breaking Changes entry. `TokenStream` is a follow-up PR that must land before 1.25.0 | done |
| 7 | 12 | `toolSpecification()` required on the requests; `execute*Guardrails(key, request)` fail fast on a key / request mismatch | done |
| 8 | 7 | `AiServiceTool` default source: `DefaultToolExecutor` -> `local(originalMethod())`, else `programmatic()` | done |
| 9 | 6 | Bind on demand (fail closed); `Map<String, ToolKey>` replaces `ToolGuardrailBinding`; tool search tool guarded; `commonGuardrailParams` in `AiServiceTokenStream` | done |
| 10 | 1 | Annotation resolution: levels add up, nearest wins within a level; runtime class for `tools(List / Map)`; JDK proxy fallback; DEBUG log on hiding | done |
| 11 | 2 addition | Rewrite note in the tool result (template + off switch on `ToolInputGuardrailsConfig`); details per Dmytro's answer to comment 7178126059 | done |
| 12 | 3, 13, 14 | Docs only: item 3 wording (Dmytro's version), item 13 rule, item 14 note. Item 9: no change | done |
| 13 | 8 | `.plans/` cleanup (local paths, personal details, session-state notes); PR description: Compatibility section as one list (JSON `null` -> `{}`, cancellation, `afterToolExecution`), Breaking Changes, drop "as on main" | done |
| Follow-up PR | 5 | `TokenStream` cancellation rule, before 1.25.0 | |

## Outcomes

All steps: UNCOMMITTED (user, 2026-11-25), awaiting one review of the whole round.

### Step 1 (items 2 and 15)

- `ToolRound`: `updateHistory` / `updateHistoryAsync` / `replace` / `isRewritten` / `rewrittenMessage` removed.
  `complete(List)` and `completeAsync(List)` take only the calls; `settleAsync` is gone, since without the history
  update it was the same as `completeAsync` (the canceled-round callers now use `completeAsync`). `ToolRound.Outcome`
  drops `messages()` (it only carried the rewritten history), and `DefaultToolRoundOutcome` drops the field and
  `withMessages`. The async loop passes its `accumulator` to `afterToolsExecuted` instead of reading it back from the
  outcome. Class Javadoc: the history keeps the call as the LLM made it.
- `ChatMemory.set` / `setAsync` NOTE: compensation only (`main` calls them for compensation).
- Javadoc: `ToolInputGuardrail.successWith(String)` (memory keeps the original call; no "before the arguments are
  stored"), `ToolInputGuardrailExecutor` (a non-object rewrite is fatal because the tool parses it).
- `tool-guardrails.md`, "success with rewrite" row: memory keeps the LLM's call, memory vs events framing, pointer to
  a custom `ChatMemory` / `ChatMemoryStore` or an AI Service output guardrail for keeping data out of the history.
- Tests: the 11 memory-rewrite assertions now assert the original call (blocking with memory, blocking without
  memory, blocking concurrent; `CompletableFuture`, `TokenStream`, `TokenStream` concurrent and reactive). Two tests
  renamed (`..._but_memory_keeps_the_original_call`, `without_chat_memory_the_next_request_carries_the_original_tool_call`).
  `ToolRoundAsyncFailuresTest`: the `settleAsync` test removed, `completeAsync(null)` kept.
- Item 15: `AnthropicToolInputRewriteWithThinkingIT` and `GoogleAiGeminiToolInputRewriteWithThinkingIT` deleted
  (`git rm`, staged). The PR no longer touches a provider module.
- Builds: `langchain4j-core` (2193 tests) and `langchain4j` (2518 tests) green, offline.
- For step 13 (PR description): the "Keeping the original tool call in the chat memory" follow-up bullet and its IT
  list are obsolete; replace with the memory rule, and mention the opt-in "persist the rewrite" as the follow-up.

### Step 2 (item 16)

- `ToolInputGuardrailResult.successWithArguments(Map<String, ?>)` (static) and `ToolInputGuardrail.successWithArguments`
  (default): serialized eagerly with `Json.toJson`; a `null` map and an unserializable value throw inside `validate`,
  so they become a fatal `ToolInputGuardrailException`.
- Tests: core result and executor tests (quotes round-trip, empty map, `null`, unserializable), AI service tests in the
  blocking and the 4 non-blocking modes. `ToolGuardrailTestSupport.appendToQuery` uses the map form.
- Docs: input results table row, new "Rewriting the arguments" section with an example.

### Step 3 (item 4)

- `ToolExecution#isResultRewritten()` (`@Internal`, package-private builder setter), set from `ToolRoundCall` (the
  result is not the tool's own result instance). `DefaultAiServices.immediateResult`: a `String` method gets the text
  of the rewritten result (fails without text), other types fail with an `IllegalConfigurationException` naming the
  tool and pointing to `Result`.
- Tests in `ToolGuardrailExecutionTest` (object without rewrite, `String`, no text, POJO, `Result`); the IT
  `ToolGuardrailReturnBehaviorTests` flipped from `null` to the guarded text. Docs: `tool-guardrails.md`, `tools.md`.

### Step 4 (item 11)

- `ToolService.fireToolExecutedEventsOfFailedRound`: the error wins over the result (same rule as `ToolRound.settled`).
- `ToolCallbackFailureTest` now covers all 4 modes, with and without guardrails (16 cases), and asserts the error
  event then the compensation event; it fails 4 cases (sync and `TokenStream`) with the old branch order.
- Docs: `tools.md` compensation section.

### Step 5 (item 10)

- `CompensableToolExecution` carries `guardedExecution` (and, from step 11, `resultTextForLlm`); `fireCompensatedEvents`
  uses the guarded one, the compensating action the raw one.
- Tests: `ToolGuardrailCompensationTest` (sanitized + rolled back, own output failure) and
  `AiServiceStreamingPublisherTest` (stream event).
- Docs/Javadoc: both `ToolCompensatedEvent`s, both tool guardrail events (data before the rewrite), `observability.md`
  rows, `tool-guardrails.md` warning in Observability and a security bullet.

### Step 6 (item 5)

- `ToolService.internalExecuteToolAsync` checks `round.isCancelled()` before `beforeToolExecution`: a call canceled
  before it started gets no callbacks, and the round records a `CancellationException`.
- `ToolCancellationBeforeStartTest` (CF and reactive; single-thread executor blocked on a latch): a queued call does
  not start; a started one runs and is rolled back. The queued case fails without the fix (callbacks fired).
- Docs: `tools.md` cancellation paragraph (with the `TokenStream` caveat), `ToolRound.startTool` Javadoc.

### Step 7 (item 12)

- `toolSpecification` required on both requests (`ensureNotNull`, non-`@Nullable` accessor). The four
  `execute*Guardrails(key, request)` methods check the request's tool name, specification and source against the key.
- Tests: request builder tests, every hand-built request in core, `langchain4j` and agentic tests now sets the spec
  (key-based ones from the key); `ToolGuardrailServiceExecuteGuardrailsTest` has the mismatch (name, spec, source; sync
  and async, input and output) and matching cases.
- Docs: unit-test example and sentence, extension points, Javadoc of the four methods.

### Step 8 (item 7)

- `AiServiceTool` default source: `local(originalMethod())` for a `DefaultToolExecutor`, otherwise `programmatic()`.
- `AiServiceToolSourceTest` (plain, proxy-like subclass, explicit `programmatic()`, lambda, `tools(Map)` end to end);
  `ToolGuardrailResolutionTest` map-tool test updated to `Local`.
- Docs: `ToolSource` table and paragraph, `ToolSource.programmatic()` / `AiServiceTool` Javadoc.

### Step 9 (item 6)

- `ToolGuardrailBinding` removed. `ToolServiceContext` has `toolGuardrailKeys()` / `Builder#toolGuardrailKeys(Map<String,
  ToolKey>)`. `ToolRound.binding(..)` binds a tool without a key on demand (spec from the context, executor from the
  context; cached for the round); no service means no guardrails.
- The deprecated `ToolService.executeTool(..)` / `startTool(..)` now apply the global guardrails to unregistered tools
  (fail closed), so their "skips the guardrails, WARN once" warning was removed (it no longer described what happens);
  `ToolServiceDeprecatedExecutionTest` rewritten for that. **Not in the round 2 decisions: flag to the user.**
- `AiServiceTokenStream` gives a framework-built context without params the common guardrail params.
- Tests: `ToolGuardrailKeysTest` (key, no key, spec from context, foreign key, no service, `TokenStream` params), the
  tool search tests flipped (blocking and IT) plus a global output guardrail on the search tool output.
- Docs: "not guarded" exceptions removed (intro, builder section, security notes), extension points.

### Step 10 (item 1)

- `ToolGuardrailChainResolver`: class level and method level nearest-wins (override chain with bridge skipping and
  generic parameter matching), runtime class of the `DefaultToolExecutor` target (`DefaultToolExecutor#targetClass()`,
  `@Internal`), declaring class for a JDK proxy, DEBUG log when a nearer annotation hides another.
- 7 tests in `ToolGuardrailResolutionTest` (interface/implementation both ways, override replaces/inherits, empty
  annotation removes, runtime class, JDK proxy, generic interface).
- Docs: "Resolution order: the levels add up" (anchor renamed, one link updated), new "Class hierarchies and
  overrides" section; Javadoc of both annotations, `ToolGuardrailService`, the resolver.

### Step 11 (item 2 addition, the rewrite note)

- `ToolInputGuardrailsConfig`: `isArgumentsRewriteNoteEnabled()` (default true), `argumentsRewriteNoteTemplate()`
  (default `<tool_arguments_changed>The arguments of this call were changed before it ran: {{arguments}}
  </tool_arguments_changed>\n{{result}}`), builder setters (template must contain `{{result}}`).
- `ToolGuardrailService#toolInputGuardrailsConfig()` (default method); `GuardedToolExecution#resultTextForLlm(String)`
  (template set by the service when an input guardrail changed the arguments and the tool ran; one-pass regex fill).
- `ToolService.toResultMessage(ToolRoundCall)` applies it to the first text content (no text: no note, DEBUG log); the
  rolled-back text gets it too (`CompensableToolExecution#resultTextForLlm`). Events, `toolExecutions()` and the
  immediate return keep the plain result.
- Tests: `ToolArgumentsRewriteNoteTest` (8), core config tests, the non-blocking rewrite test per mode, ITs.
- Docs: "What the LLM is told about a rewrite" section with a warning, table row, security bullet, Javadoc.
- Dmytro answered on 2026-11-26 ([comment 7189169590](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7189169590)),
  and the note follows it:
  1. A result without text: no note, and a WARN once per tool (`ToolService.toolsWithoutTextForTheNote`), only when
     the guardrails had a note for it (the DEBUG line is gone).
  2. `{{arguments}}` holds only the top-level arguments the LLM sent whose values changed, with their new values (a
     removed one as `null`); arguments a guardrail added are left out. Arguments of the LLM that cannot be parsed:
     all the arguments the tool ran with.
  3. Names kept: `<tool_arguments_changed>`, `{{arguments}}`, `{{result}}`.
  4. "Changed" is decided on the parsed values (`ToolGuardrailService.changedArguments`), so a re-serialization that
     only changes spacing or key order adds no note; nothing changed, no note.
- `DefaultGuardedToolExecution.ArgumentsRewriteNote(template, changedArguments)`; the builder setter is
  `argumentsRewriteNote(template, changedArguments)` (package-private).
- Tests: `ToolArgumentsRewriteNoteTest` now 12 (added arguments left out, only added -> no note, removed -> `null`,
  spacing/key order -> no note), `ToolArgumentsRewriteNoteWarningTest` (WARN once per tool; none without a rewrite).
- Docs and Javadoc (config, `tool-guardrails.md` section, warning and security bullet) updated to the new rule.

### Step 12 (items 3, 13, 14)

- `tool-guardrails.md`: Dmytro's item 3 wording under the failure bullets, the item 13 rule in "Output guardrails also
  see error results" (+ 2 tests per mode), the item 14 sentence in the async section.

### Step 13 (item 8, PR description)

- `.plans/` cleanup: `~/.hermes` draft paths, `git stash` / NEXT SESSION / force-push / worktree path notes and the
  per-task sync routine trimmed; decisions and outcomes kept.
- PR description: updated draft written outside the repo (not posted): Behavior, Compatibility (one list: cancellation,
  `afterToolExecution`, JSON `null`), Breaking Changes (2 new entries), Follow-ups (memory bullet replaced, `TokenStream`
  cancellation added), deprecated-methods bullet.

### Verification (2026-11-26, offline, after Dmytro's answer)

- `langchain4j-core` 2205, `langchain4j` 2596 (19 skipped), `langchain4j-agentic` 686, `langchain4j-mcp` 510 unit
  tests, `langchain4j-guardrails` 110, `integration-tests-guardrails` 366: all green. Revapi ran with `install` (green).
  Spotless not run (waived for this PR).
