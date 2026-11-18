# Task 06a: Refactor the `GuardrailService` Machinery for Reuse (existing AI service guardrails only)

**Type:** Refactoring (`langchain4j`, internal only; no behavior change and no public API change)

> **Why this is a separate task:** task 06b builds the tool guardrail service on top of this. The user wants to review
> the shape and format of the refactoring first, and on its own, before any tool-specific code exists. Review this task
> in isolation: its diff must not mention tools at all.
>
> **Before you start:** run the upstream sync from PLAN.md → Execution Step 2. The task 06b work is parked in a
> `git stash` (see task 06b → Status), so the working tree starts without it.

## Goal

Make the keyed lookup and execution machinery of `AbstractGuardrailService` reusable for guardrails other than the
AI service ones, so that the tool guardrail service of task 06b can reuse it instead of copying it, and so that
guardrails stay a black box that downstream frameworks can reuse. It is an **internal** refactoring: generic types are
welcome as long as they are not exposed through the public API.

## Background (verified in the current source)

- `GuardrailService` (public interface) is typed to `InputGuardrailRequest` / `InputGuardrailResult` and
  `OutputGuardrailRequest` / `OutputGuardrailResult`, is keyed by an opaque `<MethodKey>`, exposes `aiServiceClass()`,
  and has AI-service-specific default helpers (`executeGuardrails(..)` returning the `UserMessage` / the response).
- `AbstractGuardrailService` (`@Internal`) holds `Map<Object, InputGuardrailExecutor>` and
  `Map<Object, OutputGuardrailExecutor>` (filled once by `GuardrailServiceBuilder.build()`), null-key handling
  (`NULL_KEY`), `execute*` / `execute*Async` that fall back to a success result when the key has no executor, and
  caches for `hasInputGuardrails` / `hasOutputGuardrails`. `DefaultGuardrailService` (package-private) adds
  package-private test hooks.
- `GuardrailServiceBuilder` resolves annotations through `ClassMetadataProviderFactory` (keys come from
  `getNonStaticMethodsOnClass`), instantiates classes with `ClassInstanceLoader`, and has private static helpers
  (`getGuardrailClassInstance`, `getNonAnnotationBasedClassLevelGuardrails`).

## What to Do

1. Extract an **internal generic base** (name to be chosen, e.g. `KeyedGuardrailExecutors`) typed on the key, the
   request, the result and the executor. It owns: the key → executor map, `NULL_KEY` handling, `execute` /
   `executeAsync` with a caller-supplied "no guardrail" success result, and the has-guardrails check with its cache.
2. Make `AbstractGuardrailService` compose two instances of it (input and output) and delegate to them, keeping its
   behavior, its `protected` / package-private surface and the package-private test hooks
   (`getInputGuardrailMethodCount()`, ...) as they are.
3. Where it reads cleanly, turn the private static helpers of `GuardrailServiceBuilder` that task 06b needs
   (class instantiation, combining instances and classes) into shared **internal** helpers, without changing the
   precedence rules (builder > method > class, replace) or any behavior.
4. Do **not** change the public API: `GuardrailService`, `GuardrailService.Builder`, `GuardrailServiceBuilderFactory`
   and their Javadoc stay as they are. Do not deprecate `aiServiceClass()` and do not add type parameters to
   `GuardrailService` (decided with the user: internal refactoring only; revisit only if the user asks).
5. Tests: all existing guardrail tests pass **unchanged** (`DefaultGuardrailServiceTests`, `AiServiceGuardrailTests`,
   the streaming and output guardrail tests, `integration-tests-guardrails`). Add unit tests for the new generic base
   (null key, key without executor returns the default result, async path, has-guardrails caching).

## Files/Areas

- `langchain4j/src/main/java/dev/langchain4j/service/guardrail/AbstractGuardrailService.java`,
  `DefaultGuardrailService.java`, `GuardrailServiceBuilder.java`
- New internal generic base in the same package
- Tests: `langchain4j/src/test/java/dev/langchain4j/service/guardrail/`

## Key Points

- Zero behavior change: this is a pure refactoring; if a test needs changing, stop and ask the user.
- Revapi must stay green **without** new justifications (only internal types change). Check `@Internal` types are not
  reachable from public signatures.
- Keep the diff small and readable: the user reviews the shape and format of the refactoring first.
- Open questions for the review (raise, do not assume): whether the shared helpers of item 3 are worth extracting now
  or only when 06b needs them.

## Done When

- [ ] The generic base exists and `AbstractGuardrailService` is built on it, with identical behavior.
- [ ] No public signature changed (revapi passes without new entries).
- [ ] All existing guardrail tests pass unchanged; the new base has its own unit tests.
- [ ] Root `./mvnw -Pspotless validate` passes.
