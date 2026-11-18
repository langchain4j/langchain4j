# Task 11: End-to-End Integration Tests in `integration-tests-guardrails`

**Type:** Tests

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: #7106 (touches `BaseGuardrailTests`), #7107 / #7057 (public model mocks; prefer them over new scripted models if they have merged) and @dliubarskyi's rollback-before-rethrow PR.

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

- [ ] Every scenario listed above has a passing test.
- [ ] All four modes are covered for success, rewrite, non-fatal and fatal.
- [ ] The existing guardrail integration tests still pass.
