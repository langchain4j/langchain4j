# Task 05: Add `ToolInputGuardrailExecutor` / `ToolOutputGuardrailExecutor`

**Type:** New Code + small change to an `@Internal` class (`langchain4j-core`)

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: #7106 (`AbstractGuardrailExecutor` async failure handling).

## Goal

Add the two executors as subclasses of `AbstractGuardrailExecutor`. They reuse chain execution, fatal
short-circuit, failure accumulation, async execution with `CancellationChain`, `CurrentInvocation`
propagation, exception wrapping, and event firing. Also add the hook needed so that content-level output
rewrites (D8) chain correctly.

## What to Do

1. **Chaining hook.** `AbstractGuardrailExecutor` hard-wires
   `accumulatedRequest.withText(result.successfulText())` in both `executeGuardrails` (~line 164) and
   `executeGuardrailsAsync` (~line 240). Extract it into a protected method, e.g.
   `protected P rewriteRequest(P request, R result)`, whose default implementation is today's behavior. Existing
   input and output executors must behave exactly as before.
2. Add both executors to the `permits` of `AbstractGuardrailExecutor` and of its nested `GuardrailExecutorBuilder`.
3. **`ToolInputGuardrailExecutor`** (`non-sealed`, + `non-sealed` builder): implements `createFailure`,
   `createSuccess`, `createGuardrailException` (→ `ToolInputGuardrailException`), and the event builder
   (→ `ToolInputGuardrailExecutedEvent`). `execute(..)` returns the result and **does not throw on a non-fatal
   failure** (D13). Fatal → exception.
4. **`ToolOutputGuardrailExecutor`**: same as the input executor, plus:
   - Override `rewriteRequest` so a contents+`isError` rewrite is applied to the next request, not just text.
   - Override `composeResult` (as `OutputGuardrailExecutor` does) so a content-level rewrite is not lost when a
     later guardrail returns plain `success()`. Respect the rewrite-composition fix from #6425 / PR #6452.
5. Define how **multiple non-fatal failure messages** are turned into the final error text. Proposal: join the
   failure messages with `\n` in chain order, with **no added framework text** (D3). Implement a single helper
   (e.g. on the result) that task 07/08 will use, and raise the choice with the user.
6. **SPI** (moved here from task 04): add `ToolInputGuardrailExecutorBuilderFactory` /
   `ToolOutputGuardrailExecutorBuilderFactory` (`non-sealed`) in `spi.guardrail`, add them to the `permits` of
   `GuardrailExecutorBuilderFactory`, and have the executors' `builder()` look them up via `ServiceHelper` like the
   existing executors do.
7. Unit tests (core; mirror existing executor tests):
   - success, rewrite, non-fatal (no throw; failures aggregated; chain continues), fatal (throws the right
     exception; chain short-circuits)
   - output: a text rewrite followed by plain success keeps the rewrite; a content rewrite followed by a text
     rewrite (and the reverse) chain correctly; the `isError` flip is preserved
   - async path parity for all of the above; cancellation via `CancellationChain`
   - events fired once per guardrail with the right event class
   - regression: the existing input and output executor tests still pass unchanged

## Files/Areas

- `langchain4j-core/src/main/java/dev/langchain4j/guardrail/AbstractGuardrailExecutor.java` (hook + `permits`)
- New: `ToolInputGuardrailExecutor.java`, `ToolOutputGuardrailExecutor.java`
- `langchain4j-core/src/main/java/dev/langchain4j/spi/guardrail/GuardrailExecutorBuilderFactory.java`
  (`permits`) + the two new factories
- Reference: `InputGuardrailExecutor.java`, `OutputGuardrailExecutor.java` (`composeResult` override and revapi
  justification precedent in `langchain4j-core/revapi.json`)

## Key Points

- `AbstractGuardrailExecutor` is `@Internal`, so adding a protected hook is not a public API break. Still record
  the revapi impact for task 12.
- There is no retry/reprompt loop (D12); `execute()` is a single pass.
- Do not copy `OutputGuardrailExecutor` retry logic. It is hard-wired to `ChatExecutor`.

## Done When

- [x] The hook is extracted; existing input and output guardrail tests are green with no behavior change.
- [x] Both executors and builders exist, are permitted, and are reachable via SPI.
- [x] A non-fatal failure returns the result without throwing; fatal throws the tool-specific exception.
- [x] Content-level rewrites chain and compose correctly (tests).
- [x] The failure-message joining rule is implemented and agreed with the user.
- [x] `langchain4j-core` tests pass and the jacoco gate is satisfied for the new code.
