# Task 07: Wire Guardrails into `ToolService.internalExecuteTool` (Sync) and Thread `GuardrailRequestParams`

**Type:** Code Modification (`langchain4j`)

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: @dliubarskyi's rollback-before-rethrow PR (sync rethrow path in `ToolService`).

## Goal

Apply the resolved tool guardrails on the **synchronous** execution path, in the order fixed in PLAN.md
("Execution Order in `ToolService`"). Pass the AI service invocation's real `GuardrailRequestParams` (D7)
into `ToolService`, so tool guardrails see the same context as AI service guardrails and their events reach
the invocation's listener registrar.

## What to Do

1. **Thread `GuardrailRequestParams`:**
   - Find how the sync tool loop (`executeInferenceAndToolsLoop`, `ToolAwareRepromptExecutor`) is called from
     `DefaultAiServices` (~line 455 builds `commonGuardrailParam`) and pass the params down. Prefer carrying them
     in `ToolServiceContext` or an existing per-invocation carrier over adding parameters to many signatures.
   - The public `executeTool(InvocationContext, Map, ToolExecutionRequest, Consumer, Consumer)` is used by the
     streaming handler. **Do not change its signature** (public API). Add an overload or a context-based route,
     and have the old one delegate.
   - Fallback when no params are available: build a minimal `GuardrailRequestParams` with
     `userMessageTemplate = ""`, `variables = Map.of()`, and the **invocation's listener registrar** if reachable
     (otherwise events are silently lost; log at DEBUG). Document where the fallback can occur.
2. **Wire `internalExecuteTool`** (~line 2221):
   - hallucinated name → unchanged, no guardrails (D14)
   - input guardrails → on rewrite, execute with the rewritten `ToolExecutionRequest` (same id and name, new
     arguments). On non-fatal failure, **do not execute**; the result is an error `ToolExecutionResult` whose text
     is the failure message(s) verbatim (D3, joined per task 05). On fatal, throw `ToolInputGuardrailException`.
   - `executeWithErrorHandling` unchanged
   - output guardrails on **every** result, including `isError` (D5). On rewrite, map the request view back to
     `ToolExecutionResult`: **preserve `attributes`**, set the new contents and `isError`, and **drop the raw
     `result()` object** when the contents changed (it matters for `IMMEDIATE` / `IMMEDIATE_IF_LAST` tools). On
     non-fatal failure, return an error result with the verbatim message. On fatal, throw
     `ToolOutputGuardrailException`.
   - `afterToolExecution`, `ToolExecution`, `ToolExecutedEvent` and chat memory all see the **guarded** result.
   - **Input rewrite visibility (D18):** mirror AI service input guardrails, where the rewritten user message
     replaces the original downstream. After an input rewrite, the **rewritten** `ToolExecutionRequest` is what the
     output guardrail request, `afterToolExecution`, `ToolExecution`, `ToolExecutedEvent` and the compensation
     bookkeeping carry. `beforeToolExecution` has already run and saw the original. **Confirm with the user** whether
     the tool call inside the `AiMessage` stored in chat memory is rewritten too (proposal: no; see PLAN.md → "Open
     Items"), and whether an input-**rejected** tool (which never ran) reports the original request.
3. Keep, internally, what task 09 needs to apply D11's result-based rule: the **original** (pre-guardrail)
   `ToolExecutionResult` next to the guarded one, and whether the tool ran at all (input rejection). Task 09 derives
   "input rejected", "output turned success into error" (via `failure(..)` **or** a rewrite with `isError == true`)
   and "tool error" from that, rather than from which guardrail method was called. Proposal: an internal side map
   or `@Internal` holder inside `ToolService`. Do not expose it publicly or leak it to the LLM.
4. Fatal exceptions must propagate **past** `ToolExecutionErrorHandler`. Because guardrails run outside
   `executeWithErrorHandling`, this should hold naturally. Verify it, and verify `executeSequentially` /
   `executeConcurrently` fire `ToolExecutedEvent`s for the failed round the same way as a thrown tool error
   (`fireToolExecutedEventsOfFailedRound`).
5. **Investigate the built-in tool search tool** (`ToolSearchStrategy`): does it go through `toolExecutors` /
   `internalExecuteTool`? Recommend to the user whether global guardrails should apply to it (likely skip it,
   since it is a framework-internal tool, and document that).
6. **Immediate return:** confirm that an input- or output-rejected `IMMEDIATE` tool does **not** return immediately
   (`shouldReturnImmediately` checks `anyToolErrored`), and that a rewritten one returns the rewritten contents.
7. Unit tests (`ToolServiceTest` and siblings): each branch above; fatal bypasses a custom
   `ToolExecutionErrorHandler`; rewritten arguments reach the tool and are reported by `ToolExecution` / `ToolExecutedEvent` / `afterToolExecution`
   (D18); attributes preserved; `result()` dropped on
   rewrite; events go to the invocation's registrar; no-guardrail fast path unchanged; sequential and concurrent
   execution modes.

## Files/Areas

- `langchain4j/src/main/java/dev/langchain4j/service/tool/ToolService.java` (`internalExecuteTool`,
  `executeTool` overloads, `executeSequentially` / `executeConcurrently`, loop entry points)
- `langchain4j/src/main/java/dev/langchain4j/service/tool/ToolServiceContext.java`
- `langchain4j/src/main/java/dev/langchain4j/service/DefaultAiServices.java` (~455)
- `langchain4j/src/main/java/dev/langchain4j/service/ToolAwareRepromptExecutor.java`
- `langchain4j/src/test/java/dev/langchain4j/service/tool/ToolServiceTest.java` (and related tests)

## Key Points

- Pass the guardrail-relevant `InvocationContext` through `CurrentInvocation` exactly as tool execution does.
- Do **not** add any framework text to the error result (D3).
- Keep the zero-guardrail path behavior and allocation profile unchanged.
  Every tool of an AI service has a `ToolGuardrailBinding` (AiServices always sets a `ToolGuardrailService`), even with no guardrails: check `binding.hasInputGuardrails()` / `binding.hasOutputGuardrails()` before building a `ToolInputGuardrailRequest` / `ToolOutputGuardrailRequest` (added by the 06b review).

## Done When

- [ ] Sync path executes guardrails in the documented order for local, programmatic and `ToolProvider` tools.
- [ ] Real `GuardrailRequestParams` reach tool guardrails on the sync path; fallback documented and tested.
- [ ] Non-fatal → verbatim error result (input: the tool is not executed); fatal → tool-specific exception that
      bypasses `ToolExecutionErrorHandler`.
- [ ] Output guardrails run on error results; rewrite mapping preserves attributes and drops `result()`.
- [ ] After an input rewrite, everything downstream carries the rewritten request (D18); the chat memory question is
      answered by the user.
- [ ] The original and guarded results (and whether the tool ran) are available internally for task 09.
- [ ] Tool-search and immediate-return behaviors verified and reported.
- [ ] `langchain4j` tests pass.
