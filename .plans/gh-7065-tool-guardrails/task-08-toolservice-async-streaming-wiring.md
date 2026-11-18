# Task 08: Wire Guardrails into the Async and Streaming Paths

**Type:** Code Modification (`langchain4j`)

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: #7106 (async failure routing) and @dliubarskyi's rollback-before-rethrow PR (`TokenStream`).

## Goal

Give the non-blocking paths (`CompletableFuture` AI services, `TokenStream`, reactive `Flow.Publisher`)
exactly the same tool guardrail behavior as the sync path from task 07. Use the guardrail executors' async
API, propagate cancellation, and make sure a **fatal failure fails the future / goes to `onError`**
instead of being thrown directly (D4).

## What to Do

1. **`internalExecuteToolAsync`** (~line 1983): mirror the task 07 order with futures:
   input guardrails (`executeAsync`) → `executeWithErrorHandlingAsync` → output guardrails (`executeAsync`) →
   `afterToolExecution`. Any guardrail exception, including one thrown synchronously while starting, must become
   a **failed future**. Keep the existing `handle(..)` block so the "after" notification still balances the
   "before" one.
2. **`startTool(..)`** (used by `AiServiceStreamingEventPublisher.java:393`) and the public `executeTool(..)` used
   by `AiServiceStreamingResponseHandler.java:571`: make sure both reach the guarded paths with the right
   `GuardrailRequestParams`.
   - **Known gap carried over from task 07:** the public `executeTool(InvocationContext, Map<String, ToolExecutor>,
     ToolExecutionRequest, Consumer, Consumer)` has only the executor map, so it calls `internalExecuteTool` with no
     `ToolServiceContext` and runs **without** tool guardrails today. The `TokenStream` path therefore bypasses tool
     guardrails until this task. Keep that signature (public API) and add a context-based route the streaming handler
     uses instead (it already has the `ToolServiceContext` with bindings and, once threaded, the params).
   - That route must return the **effective request** too, not only the result (D18): the handler keys its round
     by request, builds the `ToolExecutionResultMessage`s, fires `ToolExecutedEvent`s and feeds compensation. The guardrail
     mechanics of a call are public: add the non-blocking counterpart of `ToolGuardrailService#executeGuardrails`
     (`executeGuardrailsAsync`, returning `CompletableFuture<GuardedToolExecution>`, with the `tool` function
     returning a future; fatal fails the future, D4), mirroring `GuardrailService#executeGuardrailsAsync`. Reuse task
     07's package-private `ToolRound` for the loop bookkeeping (`start`, `execute`, `complete` → `Outcome`,
     `reportedRequest`), so the async loop and the streaming classes stay free of guardrail knowledge (user
     decision, 2026-11-17). The streaming handler and publisher are in `service`, so make `ToolRound` `public` +
     `@Internal` (like `ToolGuardrailBinding`), or route through `ToolService` methods. It needs an async `execute`
     and an async `complete` (`setAsync` on the memory).
   - Rewrite the tool calls of the stored `AiMessage` after an input rewrite, as the sync loop does
     (`ToolRound#complete`, D18 + the 2026-11-17 chat memory decision), on every streaming and async
     path, with the async memory methods (`setAsync`) where the path is non-blocking.
   - Failed-round events must report the request as the round knows it (`ToolRound#reportedRequest`);
     `combineToolResultsCollectingErrors` and the streaming publisher need the same.
3. **Thread `GuardrailRequestParams`** on every non-blocking path, reusing the existing params:
   - `DefaultAiServices.java:599-683` (reactive / `commonGuardrailParam`)
   - `AiServiceStreamingResponseHandler.java:73, 472-483`
   - `AiServiceStreamingEventPublisher.java:140, 725-731`
   - `AiServiceTokenStream.java:58, 297-300` (note the per-run `invocationContext` rebuild)
   - `AiServiceTokenStreamParameters.java`

   Where `commonGuardrailParams` can be `null` (some streaming paths), use the task 07 fallback, preserving the
   registrar.
4. **Cancellation:** guardrail execution participates in the existing cancellation (`CancellationChain` in the
   executors; the `cancellation` future in the async loop). A canceled invocation must not start guardrails for
   tools that haven't started, and must not swallow `CancellationException`.
5. **Error routing:** confirm a fatal guardrail failure in streaming surfaces via `onError` (`TokenStream`) and
   `onError` / failed publisher (reactive), with the `Tool*GuardrailException` as the cause. Confirm
   `combineToolResultsCollectingErrors` treats it as the round's `firstError` (so compensation runs first, as for
   a thrown tool error; see task 09).
6. Tests: async / `TokenStream` / reactive parity for success, input rewrite, output rewrite (content-level),
   non-fatal (input: tool not executed), fatal (failed future / `onError`; `ToolExecutionErrorHandler` bypassed),
   async guardrails (`validateAsync` overridden), cancellation mid-guardrail, and events delivered to the
   invocation's registrar.

## Files/Areas

- `langchain4j/src/main/java/dev/langchain4j/service/tool/ToolService.java` (`internalExecuteToolAsync`,
  `executeToolAsync`, `startTool`, async loop)
- `langchain4j/src/main/java/dev/langchain4j/service/AiServiceStreamingResponseHandler.java`
- `langchain4j/src/main/java/dev/langchain4j/service/AiServiceStreamingEventPublisher.java`
- `langchain4j/src/main/java/dev/langchain4j/service/AiServiceTokenStream.java`,
  `AiServiceTokenStreamParameters.java`
- `langchain4j/src/main/java/dev/langchain4j/service/DefaultAiServices.java`
- Existing async/streaming tool tests in `langchain4j/src/test/java/dev/langchain4j/service/`

## Key Points

- Never block the model-delivery thread: use `executeAsync` on the guardrail executors and do not `join()`.
- Preserve `CurrentInvocation` / captured context across async stages (`CapturedContextSupport`).
- Keep the internal original-vs-guarded result tracking from task 07 consistent across both paths, and report the
  rewritten request after an input rewrite exactly as the sync path does (D18).
- #7106 (@dliubarskyi, open) makes `AbstractGuardrailExecutor.executeGuardrailsAsync(..)` / `validateAsync(..)`
  report failures through the future instead of throwing. If it has merged, rely on it; otherwise make sure a
  synchronously thrown guardrail error still fails the future here.

## Done When

- [ ] All non-blocking modes apply tool guardrails with behavior identical to sync (parity tests).
- [ ] Fatal failures surface as failed futures / `onError`, never as exceptions thrown from callbacks.
- [ ] Real `GuardrailRequestParams` reach guardrails on all paths (fallback only where documented).
- [ ] Cancellation is respected and tested.
- [ ] `langchain4j` tests pass.

## Note from the 06b review

Every tool of an AI service has a `ToolGuardrailBinding`, even with no guardrails. Check `binding.hasInputGuardrails()` / `binding.hasOutputGuardrails()` before building a guardrail request, to keep the zero-guardrail path allocation-free (same as Task 07).
