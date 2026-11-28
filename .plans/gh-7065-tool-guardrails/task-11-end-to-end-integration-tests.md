# Task 11: End-to-End Integration Tests in `integration-tests-guardrails`

**Type:** Tests

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: [#7106](https://github.com/langchain4j/langchain4j/pull/7106) (touches `BaseGuardrailTests`), [#7107](https://github.com/langchain4j/langchain4j/pull/7107) / [#7057](https://github.com/langchain4j/langchain4j/pull/7057) (public model mocks; prefer them over new scripted models if they have merged) and @dliubarskyi's rollback-before-rethrow PR.

## Goal

Prove end to end, through real `AiServices`, that tool guardrails behave identically across invocation
modes and tool kinds, and pin every decision in the PLAN.md Decision Record with at least one
user-visible test.

## What to Do

Add new test classes (for example `ToolInputGuardrailTests`, `ToolOutputGuardrailTests`,
`ToolGuardrailPrecedenceTests`, `ToolGuardrailStreamingTests`) next to the existing guardrail tests. Reuse
`BaseGuardrailTests`, scripted chat models in the style of `MyChatModel` / `EchoChatModel` (a model that emits
tool calls and then a final answer), and `com.example.SingletonClassInstanceFactory` for class-based guardrails.

Matrix to cover (do not cross-multiply everything; pick the cells that exercise distinct code paths):

| Dimension | Values |
|---|---|
| Mode | sync, `CompletableFuture`, `TokenStream`, reactive `Flow.Publisher` |
| Tool kind | `@Tool` method, `tools(Map)`, `tools(List<AiServiceTool>)`, `ToolProvider` |
| Config surface | method annotation, class annotation, `AiServices` global (instances and classes), `AiServiceTool` |
| Outcome | success, rewrite (text and content-level), non-fatal (LLM self-corrects), fatal |

Specific scenarios that must exist:
- input non-fatal failure → the tool is **not** executed and the LLM receives the failure message **verbatim** (D3)
- output non-fatal failure on an indirect-injection-style result → the LLM never sees the original text (D3)
- fatal → `ToolInputGuardrailException` / `ToolOutputGuardrailException` (sync) and failed future / `onError`
  (non-blocking); a custom `ToolExecutionErrorHandler` is **not** invoked (D4)
- output guardrail runs on an error result (thrown tool exception with remote-looking text) (D5)
- resolution order: builder, class and method guardrails **all run, in that order** (observable through chained
  rewrites and events); an empty global list means no global guardrails (D6)
- @PSchmitz-Valckenberg's scenario (discussioncomment-19603638): a fetching tool with its own
  `@ToolOutputGuardrails` and a global injection-check guardrail on the builder → both run; the injected result is
  blocked by the global guardrail (D6)
- @dliubarskyi's scenario (discussioncomment-19659065): a class-level injection check plus a method-level size limit
  → both run for that method (D6)
- `requestParams()` carries the real chat memory, memory ID, user message template and variables; events reach a
  registered `ToolInputGuardrailExecutedListener` / `ToolOutputGuardrailExecutedListener` and appear in
  `AiServiceInvocationSummaryEvent` (D7)
- multi-content result (text + image, two text blocks) → the guardrail sees all contents, nothing throws, and a
  content-level rewrite works (D8)
- `ToolSource` is visible and correct for each tool kind (D9)
- input rewrite: the tool runs with the rewritten arguments, and `ToolExecution` / `ToolExecutedEvent` report them (D18)
- compensation with `compensateOnToolErrors(true)`: output-rejected tool (via `failure(..)` and via a rewrite with
  `isError == true`) rolled back with its original result and `TOOL_OUTPUT_GUARDRAIL_FAILED`; a rewrite keeping
  `isError == false` rolls nothing back; input-rejected tool triggers rollback of others (including earlier rounds)
  with `TOOL_INPUT_GUARDRAIL_FAILED`; an error result sanitized with `successWith(String)` stays an error and still
  rolls the others back (`TOOL_EXECUTION_FAILED`); a fatal rejection rolls back in all modes once @dliubarskyi's
  rollback-before-rethrow PR is in (D11)
- immediate-return tool + rewrite / rejection
- AI service proxy used as a tool: `@InputGuardrails` and `@ToolInputGuardrails` on the same interface each apply
  to their own side only

## Files/Areas

- `integration-tests/integration-tests-guardrails/src/test/java/dev/langchain4j/service/guardrail/` (new classes)
- `integration-tests/integration-tests-guardrails/src/main/java/com/example/` (validation helpers if needed)
- MCP end-to-end coverage is in task 10, not here (this module has no MCP dependency; do not add one unless the
  user agrees).

## Key Points

- Follow the existing test style; AssertJ only, chained assertions.
- These tests run against the locally installed snapshot: `mvn install -pl langchain4j-core,langchain4j -DskipTests -Djacoco.skip=true`
  first, then `mvn -pl integration-tests/integration-tests-guardrails test`. A pre-existing BlockHound failure
  (`JdkHttpClientPublisherNonBlockingIT`) is unrelated (see the previous plan's notes).
- @PSchmitz-Valckenberg's dedicated injection-case tests are a **follow-up** (settled with him in the discussion).
  Leave clear extension points (a reusable scripted model, shared fixtures) so they can slot in.

## Done When

- [ ] Every scenario listed above has a passing test. **Open (see Outcome):** the fatal-rejection rollback in the sync and `TokenStream` modes (waits for [#7168](https://github.com/langchain4j/langchain4j/pull/7168), task 16) and compensation in the `TokenStream` mode (that mode has none, with or without guardrails).
- [x] All four modes are covered for success, rewrite, non-fatal and fatal (`ToolInputGuardrailTests` 46, `ToolOutputGuardrailTests` 54: each outcome is parameterized over `Mode`).
- [x] The existing guardrail integration tests still pass (the module runs 346 tests: the 116 existing ones plus 230 new, 0 failures, 0 skipped).


## Outcome

**Implemented 2026-11-18; reviewed by the user, committed as `352dfea94` (tests) and `340dfdd51` (plan) and pushed to the fork as a fast-forward.** Upstream state at the start: [#7106](https://github.com/langchain4j/langchain4j/pull/7106) and [#7168](https://github.com/langchain4j/langchain4j/pull/7168) still open; the local branch and `origin/gh-7065-tool-guardrails` are both 0 behind / 12 ahead of `upstream/main`.

### What was built

All in `integration-tests/integration-tests-guardrails`; 230 new tests in 8 classes, plus one shared fixture class. No main code and no production class was touched.

| File (tests) | What it pins |
|---|---|
| `ToolGuardrailTestSupport` (fixtures) | Shared fixtures (the extension point for @PSchmitz-Valckenberg's injection tests): `run(Mode, responses, configuration)` runs one AI service in `SYNC` / `COMPLETABLE_FUTURE` / `TOKEN_STREAM` / `REACTIVE` and returns a `Run` (answer or failure, memory, model requests, every event); `ToolKind` registers the `search` tool as `@Tool` method / `tools(Map)` / `tools(List<AiServiceTool>)` / `ToolProvider`; `SearchTool` (records its arguments; answers text, contents or a failure); guardrails that work in every mode (`input(..)`, `output(..)`, `onInputOf(..)`, `onOutputOf(..)`, `Blocking*Guardrail` bases for class-based ones) |
| `ToolInputGuardrailTests` (46) | success, rewrite (D18), non-fatal (D3, verbatim, tool not run), fatal (D4) x 4 modes; each outcome x 4 tool kinds (sync); `AiServiceTool` guardrails; `argumentsAsMap()` |
| `ToolOutputGuardrailTests` (54) | success, non-fatal on an injected result (the LLM, memory, `ToolExecution` and the event never see the original), text and content rewrite, multi-content (text + image, two text blocks, content rewrite; D8), error results (D5), fatal (D4, error handler not called, call throws only in sync) x 4 modes; each outcome x 4 tool kinds |
| `ToolGuardrailPrecedenceTests` (52) | D6 order builder, class, method (chained rewrites and events), global classes and instances, `AiServiceTool` slot, empty global list, dedup of one class at two levels, and the two discussion scenarios (@PSchmitz-Valckenberg: fetching tool with own guardrail + global injection check; @dliubarskyi: class-level injection check + method-level size limit). **Counterparts of the AI service precedence tests** (`InputGuardrailOnClassTest`, `OutputGuardrailOnClassTests`, `*OnClassAndMethod*`, `*Chain*`), with `SingletonClassInstanceFactory` spies: builder alone, class alone, method alone (only that tool), all three at once (the method level adds, where the AI service one replaces), once per call over three rounds, one instance for a class named at two levels, `null` global lists, a class annotation on an interface and on a superclass |
| `ToolGuardrailContextTests` (23) | D7 `requestParams()` (the real chat memory instance, memory ID, template, variables), events reach the listeners and `AiServiceInvocationSummaryEvent`, no cross-delivery to AI service guardrail listeners; D9 `ToolSource` per tool kind (`Local` for `@Tool`, `programmatic` otherwise), an application-set `ToolSource.mcp(..)` routed on by a global guardrail; an AI service used as a tool (`@InputGuardrails` and `@ToolInputGuardrails` on one interface each fire once, for their own side) |
| `ToolGuardrailCompensationTests` (25) | D11 the whole table: output `failure(..)` and rewrite with `isError == true` roll back all incl. the rejected tool with its original result and `TOOL_OUTPUT_GUARDRAIL_FAILED`; a rewrite keeping `isError == false` rolls nothing back; input rejection rolls back others incl. earlier rounds with `TOOL_INPUT_GUARDRAIL_FAILED`, the rejected tool is not compensable; an error result sanitized with `successWith(String)` stays an error (`TOOL_EXECUTION_FAILED`); compensation off; fatal input / output in the `CompletableFuture` mode |
| `ToolGuardrailRequestParamsTests` (9) | Counterpart of `InputGuardrailPromptTemplateTests`: template and variables for no parameters, memory ID alone, one unnamed parameter, memory ID + parameter, three parameters, a list, memory ID + list; the chat memory is the invocation's own instance; no chat memory means `null` |
| `ToolGuardrailToolRoundTests` (17) | Several calls in one round, sequential and concurrent (`executeToolsConcurrently()`) x 4 modes: pass, rewrite, input rejection and output rejection each keep their own outcome, results reach the LLM in call order, event counts; a fatal input failure in a concurrent round fails the invocation; a hallucinated tool name skips the guardrails (D14); the built-in tool search tool is not guarded but the tool it finds is |
| `ToolGuardrailReturnBehaviorTests` (4) | `IMMEDIATE`: rewrite of output and input, rejection by input and by output does not return immediately |

### Deviations and findings

1. **`TokenStream` has no compensation at all, with or without guardrails.** A throwaway probe (deleted afterwards) with no guardrail, `credit` + a throwing `withdraw`, `compensateOnToolErrors(true)`: sync and `CompletableFuture` log `[credit, withdraw, uncredit]`, reactive `[withdraw, credit, uncredit]`, `TokenStream` `[credit, withdraw]`. So `ToolGuardrailCompensationTests` skips `TOKEN_STREAM` (an `@CompensatingModes` annotation says why) instead of asserting a rollback that never happens. Task 09's Outcome said the `TokenStream` compensation path was "not covered by a dedicated test"; this is the reason. It is existing behavior, not caused by guardrails. Whether [#7168](https://github.com/langchain4j/langchain4j/pull/7168) ("in every AI Service mode") changes it is for task 16 to check.
2. **Fatal rollback in the sync and `TokenStream` modes is not tested**, as the task file said (it waits for [#7168](https://github.com/langchain4j/langchain4j/pull/7168); task 16). The `CompletableFuture` mode is covered. The sync test would be: fatal output rejection, `credit` rolled back, expected to fail until [#7168](https://github.com/langchain4j/langchain4j/pull/7168) lands.
3. **`requestParams().variables()` contains the unnamed `@MemoryId` argument** (`{"arg0": "user-1", "topic": "cats"}`), as for AI service guardrails (existing behavior), so the test asserts `containsEntry("topic", ..)` rather than the whole map.
4. **`langchain4j-test` is now a `test`-scope dependency of the module** (`${langchain4j.beta.version}`, like `integration-tests-decision-models`), for `MockChatModel` / `MockStreamingChatModel` ([#7057](https://github.com/langchain4j/langchain4j/pull/7057) / [#7107](https://github.com/langchain4j/langchain4j/pull/7107), merged). They replace new scripted models; `MyChatModel` / `EchoChatModel` are untouched.
5. **A lambda tool guardrail fails in the `CompletableFuture` and reactive modes** (no `validateAsync`), so the shared `input(..)` / `output(..)` helpers and the `Blocking*Guardrail` bases implement it; the AI service tool guardrail helper does the same. (Already documented for task 13.)
6. The class-based tests use `toolInputGuardrailClasses(List.of(..))`: the varargs overload creates a generic array and raises an unchecked warning in the IDE.

### Verification

- `./mvnw -o test -pl integration-tests/integration-tests-guardrails`: **346 tests, 0 failures, 0 errors, 0 skipped** (116 existing + 230 new), against the locally installed `langchain4j-core`, `langchain4j` and `langchain4j-test` built from this branch.
- **Mutation-checked** (each applied to `ToolGuardrailService`, tests re-run, then restored and reinstalled): a `"validation failed: "` prefix on input failures fails 11 tests; output guardrails blind to error results fail 7; a rewrite that ignores the guardrail's `isError` fails 7.
- Root-style `-Pspotless spotless:check -pl integration-tests/integration-tests-guardrails` passes. IntelliJ lint: no unused imports; remaining warnings are expected (`ClassCanBeRecord` on the private subscriber, "unused" on the `@Tool` / `@CompensateFor` methods of `Bank` that the framework calls by reflection).
- Second round of mutations (`ToolGuardrailChainResolver`, restored afterwards): no dedup of classes fails 2 tests, no interface annotations 3, no superclass annotations 1.
- Not run: the other two profiles / the full root build (tests only; no production change).
