# Task 03: Add the Tool Guardrail Result Types, Interfaces and Exceptions

**Type:** New Code (public API, `langchain4j-core`)

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: none specific.

## Goal

Add `ToolInputGuardrailResult` / `ToolOutputGuardrailResult` (each with a nested `Failure`), the
`ToolInputGuardrail` / `ToolOutputGuardrail` interfaces, and `ToolInputGuardrailException` /
`ToolOutputGuardrailException`, and register them in the sealed hierarchies.

## What to Do

1. **Results** (follow `InputGuardrailResult` / `OutputGuardrailResult`):
   - `ToolInputGuardrailResult`: `success()`, `successWith(String rewrittenArguments)`, `failure(..)`, `fatal(..)`.
   - `ToolOutputGuardrailResult`: `success()`, `successWith(String)` (text shortcut), **and** a content-level
     rewrite, e.g. `successWith(List<Content> contents, boolean isError)`. This carries the rewritten contents
     and `isError` (D8; the core analog of Quarkus' `successWith(ToolExecutionResult)`). `successfulText()` must
     stay meaningful for the text shortcut.
   - Nested `Failure` types implementing `GuardrailResult.Failure`.
   - Add both to `GuardrailResult` `permits`, and both `Failure`s to `GuardrailResult.Failure` `permits`.
2. **Interfaces** (D2):
   ```java
   public interface ToolInputGuardrail  extends Guardrail<ToolInputGuardrailRequest,  ToolInputGuardrailResult>  { … }
   public interface ToolOutputGuardrail extends Guardrail<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> { … }
   ```
   - `validate(request)` is **abstract**: there is no default `failure("Validation not implemented")`.
   - Default helpers that delegate to the result factories: `success()`, `successWith(..)`, `failure(String)`,
     `failure(String, Throwable)`, `fatal(String)`, `fatal(String, Throwable)`. The output interface also gets the
     contents/`isError` rewrite helper.
   - **No** `retry`, `reprompt`, or `*WithMessageRemoval` (D12).
   - `validateAsync()` is inherited unchanged from `Guardrail`.
3. **Exceptions:** `ToolInputGuardrailException` / `ToolOutputGuardrailException` extending
   `GuardrailException`, added to its `permits`. Follow the constructors of `InputGuardrailException`.
4. Unit tests: result factories, `isSuccess` / `hasRewrittenResult` / `isFatal` semantics, content rewrite
   carrying `isError`, failure aggregation via `createFailure`-style construction, and the interface helpers
   producing the right results.

## Files/Areas

- `langchain4j-core/src/main/java/dev/langchain4j/guardrail/GuardrailResult.java` (both `permits` clauses)
- `langchain4j-core/src/main/java/dev/langchain4j/guardrail/GuardrailException.java` (`permits`)
- New in `dev.langchain4j.guardrail`: `ToolInputGuardrailResult`, `ToolOutputGuardrailResult`, `ToolInputGuardrail`,
  `ToolOutputGuardrail`, `ToolInputGuardrailException`, `ToolOutputGuardrailException`
- Reference: `InputGuardrail.java`, `InputGuardrailResult.java`, `OutputGuardrailResult.java` (`successfulResult`
  precedent), `InputGuardrailException.java`

## Key Points

- All types share one package (sealing constraint).
- The input `successWith(String)` means "rewritten arguments JSON"; the output `successWith(String)` means
  "rewritten text". Make this explicit in the Javadoc.
- Javadoc on `failure(..)` must say the message goes to the LLM **verbatim** (D3), must not quote the checked
  content, and that `fatal(..)` aborts the invocation (D4).
- Keep the `toString()` / message formatting consistent with the existing results; events and logging rely on it.

## Done When

- [x] Results, interfaces and exceptions exist and are permitted in every sealed parent.
- [x] `validate` is abstract on both interfaces; there are no retry/reprompt helpers.
- [x] The output result supports both a text and a contents+`isError` rewrite.
- [x] Unit tests pass; `langchain4j-core` compiles with no new warnings.
