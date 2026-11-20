# Task 17: Make `ToolGuardrailService` a Standalone Interface (review rework)

**Type:** Refactoring + public API change (`langchain4j`; `@Experimental` tool API only, AI service API back to `main`)

> **Why:** @dliubarskyi's design review of PR #7202
> ([comment](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7037036391)), agreed in
> [our reply](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7040595689) and confirmed in
> [his answer](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7040946058). It reverses the 06a
> decisions on the shared public parent, the sealing of the hierarchy and the deprecations (see PLAN.md →
> `### Review Rework (2026-11-20)`).
>
> **Before you start:** sync with upstream (merge `upstream/main` into the branch, the rule since 2026-11-19) and make
> sure the branch contains @dliubarskyi's four review commits (`3fdea6601`, `7e4d448cb`, `9ca25227f`, `eb0f9c499`).
> The rework goes **on top of them**. Check his answers to the open questions on
> [our second reply](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7041291412) first.

## Goal

`ToolGuardrailService` stands on its own, typed by `ToolKey`, and the AI service guardrail API
(`GuardrailService`, `GuardrailService.Builder`, `AbstractGuardrailService`) is exactly as on `main`. The reuse between
the two stays, but only in internal classes. The unified contract for both kinds is left for 2.0.

## What to Do

1. **Remove the shared public parent:** delete `GenericGuardrailService` (and its nested `Builder`). Delete
   `AbstractKeyedGuardrailService` and `AbstractKeyedGuardrailServiceBuilder` too, unless a review shows they still have
   two users once the AI service side is back to `main` (expected: one user each, so fold them in).
2. **`ToolGuardrailService`** (`@Experimental`, no supertype):
   - declares `targetClass()`, `bind(..)`, `executeInputGuardrails(ToolKey, ToolInputGuardrailRequest)`,
     `executeOutputGuardrails(ToolKey, ToolOutputGuardrailRequest)`, their `*Async` variants, and
     `hasInputGuardrails(ToolKey)` / `hasOutputGuardrails(ToolKey)`;
   - `executeGuardrails(..)` / `executeGuardrailsAsync(..)` lose the `toolSpecification` and `toolSource` parameters
     (they come from the key);
   - `ToolGuardrailService.Builder` declares its own setters with the same shape as today (instances, classes, configs:
     6 list setters, 4 varargs defaults, `build()`), and `builder(Class)` keeps the `ToolGuardrailServiceBuilderFactory`
     SPI lookup.
3. **`ToolKey`** gets `toolSpecification()` and `toolSource()` (with `@Nullable` where they can be absent, following
   @dliubarskyi's `@Nullable` pass). `DefaultToolKey` fills them in at `bind(..)`. `ToolGuardrailBinding` and
   `ToolRound` stop carrying and passing them separately.
4. **Fail fast on foreign keys** (his answers to question 1,
   [first](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7040946058) and
   [second](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7041470460)): an
   `IllegalArgumentException` for a `null` key and for any key the default service did not return from `bind(..)`,
   **including a `DefaultToolKey` bound by another service instance** (owner check). Why the owner check: such a key runs
   the other service's chain, so the global guardrails of the service it is passed to are skipped without any signal.
   `7e4d448cb` already rejects non-`DefaultToolKey` keys, but `null` still returns empty and there is no owner check.
   `DefaultToolKey` records the service that bound it (by identity, not `equals`), and every lookup (`execute*`,
   `execute*Async`, `has*`, `executeGuardrails*`) checks it; the async methods fail their future instead of throwing.
   Tests: `null`, a hand-written `ToolKey`, and a key of a second service built from the same builder settings.
5. **Internal implementation:** `DefaultToolGuardrailService` composes two `KeyedGuardrailExecutors`;
   `ToolGuardrailServiceBuilder` delegates its setters to two `GuardrailBuilderSettings`. `ToolGuardrailChainResolver`
   stays.
6. **AI service side back to `main`'s public shape** (his answer to question 2: the public and protected API, the
   supertypes and the behavior):
   - `GuardrailService` and `GuardrailService.Builder`: as on `main` (no `non-sealed`, no redeclarations, no
     `targetClass()`, `aiServiceClass()` not deprecated).
   - `AbstractGuardrailService`: as on `main` (no longer extends `AbstractKeyedGuardrailService`, not deprecated, same
     protected constructor). Its internals may delegate to `KeyedGuardrailExecutors`.
   - `DefaultGuardrailService`: bring the `main` name back (it was renamed to `DefaultAiServiceGuardrailService`), unless
     keeping the rename is clearly better; decide with the user.
   - `GuardrailServiceBuilder` may stay on `GuardrailBuilderSettings` (internal; agreed).
   - Remove the tests that pin the removed hierarchy (`GuardrailServiceBuilderHierarchyTest`, the `permits` checks in
     `AbstractKeyedGuardrailServiceTest`), and keep the behavior tests.
7. **Tool guardrail annotations go through `ClassMetadataProviderFactory` (user decision, 2026-11-20, agreeing with
   [Dmytro's answer to Q2](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7041470460); supersedes the
   earlier reflection-only proposal).**
   - **Class side:** `findClassAnnotation(..)` asks the factory only (`getAnnotation(Class, annotationType)` over the class
     hierarchy) and keeps its answer: **remove the reflection fallback** that `7e4d448cb` added. The default
     (reflection-based) factory reads the annotation by reflection anyway, so nothing changes without a framework factory.
   - **Method side:** settled in task 18 (`resolveMethodKey`). Until that PR lands, PR #7202 reads method annotations by
     reflection (`Method.getAnnotation(..)`), and does not call `resolveMethodKey` (it is not on this branch any more).
     Say so in a short code comment where the lookup is, and in task 18.
   - **Javadoc on `ClassMetadataProviderFactory`** (his request): it is also asked about tool classes (the class of the
     object a `@Tool` method was found on, and its superclasses), for `@ToolInputGuardrails` / `@ToolOutputGuardrails`;
     a factory must return `Optional.empty()` for an annotation or a class it has no metadata for, and must not throw.
     Same note in `_guardrails-extension-points.mdx`.
   - **Why the factory (user, 2026-11-20):** it is what the SPI exists for (frameworks supply annotation metadata their
     own way, for example from a build-time index); it keeps tool guardrails on the same pattern as AI service guardrails,
     which is what Quarkus reuses; and the only real framework implementation on GitHub is Quarkus's (CDI and Micronaut
     do not implement it; Micronaut has its own `MicronautGuardrailServiceBuilder`), so the cost is one factory, which
     Quarkus updates when it moves to 1.25.
   - **Known consequence for Quarkus:** `QuarkusClassMetadataProviderFactory` throws
     `IllegalArgumentException("Unsupported guardrail annotation")` for any annotation other than `@InputGuardrails` /
     `@OutputGuardrails`. Since `DefaultAiServices.build()` binds every tool, a Quarkus app that uses core's programmatic
     `AiServices.builder()` with tools fails at `build()` until Quarkus's factory returns empty for (or answers) the tool
     guardrail annotations (read from both sources, not run). Recorded on quarkus-langchain4j#3162 (comment edited
     2026-11-20); mention it to Dmytro as an FYI.
   - Frameworks with their own tool loop and no `Method` for a tool (Quarkus) still pass method-level guardrails through
     `AiServiceTool` and call `bind(null, tool)` (see PLAN.md → "Quarkus reuse path"). Document this in the Javadoc of
     `ToolGuardrailService#bind(..)` and `@ToolInputGuardrails` / `@ToolOutputGuardrails`.
   - Remove the tests that pin the class-side fallback (`ToolGuardrailChainResolverAnnotationsTest`), and add: a factory's
     answer for a tool class is used; a factory that returns empty means no class-level guardrails, even when the class
     carries the annotation (the factory wins).
7a. **Contract test for the framework reuse path** (test our own contract, not Quarkus's shape): in
   `langchain4j/src/test/java/dev/langchain4j/service/guardrail/`, a test that uses `ToolGuardrailService` directly,
   without `AiServices`:
   - `ToolGuardrailService.builder(SomeAiService.class)` with a global input guardrail, `build()`;
   - an `AiServiceTool` with a custom `ToolExecutor` (not `DefaultToolExecutor`), a `ToolSource`, and input and output
     guardrail instances;
   - `bind(null, tool)`, then `executeGuardrails(key, request, requestParams, tool)`;
   - assert the chain order (global, then the `AiServiceTool` guardrails), a rewrite reaching the tool, a non-fatal
     failure becoming an error result with the message verbatim, and the `ToolKey` carrying the specification and
     source.
   Mutation-check it.
8. **Formatting: do NOT run spotless (user decision, 2026-11-20).** @dliubarskyi asked to keep formatting changes to the
   lines the PR actually changes, so lightly touched files (`DefaultAiServices`, `AiServiceStreamingEventPublisher`,
   `ToolService`, ...) are not reformatted again. Do not run `spotless:apply`, and do not let the IDE reformat whole files
   (no "reformat file" / optimize-imports on save over unrelated lines). Format new code by hand in the project style.
   Known consequence: the CI `spotless` job (`mvn -Pspotless validate`, `ratchetFrom origin/main`, whole changed files)
   will fail. As of `eb0f9c499` it flags 19 files, mostly the import order of imports the review commits added, plus
   whole-statement formatting in `CrossAgentCompensationTest`, `McpToolProviderTest` and `AiServiceInvocationAssert`; the
   three files he named pass. Fixing an import order on lines the PR added is allowed (it is a PR line), but is not
   required. Also: in `ToolGuardrailChainResolver`, the `annotationGuardrails` field sits between the constructor's
   Javadoc and the constructor; move it above the Javadoc.
9. **His answers to questions 3 and 4:**
   - Q3 (cancellation after the executor hop, `ToolRound.startOn`): not intended. Propagate the cancellation of the
     returned future to the tool's own future, **and** check the cancellation again when the task starts on the
     executor, so a tool that was queued before the cancel does not start after it. Test both: a cancel while the tool
     runs reaches its future, and a cancel while the task is queued (a paused executor) means the tool never starts.
   - Q4 (Jackson `FAIL_ON_TRAILING_TOKENS` instead of `endsAfterFirstObject`): **keep the scanner**. `Json` goes through
     the `JsonCodec` SPI, so a user's codec (or the Jackson 3 one) can be behind it, and a Jackson flag would bypass it.
     No change.
10. **Docs:** `tool-guardrails.md`, `_guardrails-extension-points.mdx`, `guardrails.md`, `observability.md`,
    `non-blocking.md` and every Javadoc that mentions `GenericGuardrailService`, `AbstractKeyedGuardrailService`,
    `targetClass()` on `GuardrailService`, the deprecations, or the `executeGuardrails(..)` signature with the spec and
    source parameters (`grep` them all).
11. **Revapi:** remove the justifications this PR added for the AI service hierarchy (if any); against 1.24.0, revapi
    must pass with **no new justifications** for `GuardrailService`, its `Builder` and `AbstractGuardrailService`.

## Files/Areas

- `langchain4j/src/main/java/dev/langchain4j/service/guardrail/`: `GenericGuardrailService`, `GuardrailService`,
  `AbstractGuardrailService`, `AbstractKeyedGuardrailService`, `AbstractKeyedGuardrailServiceBuilder`,
  `DefaultAiServiceGuardrailService`, `GuardrailServiceBuilder`, `ToolGuardrailService`, `ToolGuardrailServiceBuilder`,
  `DefaultToolGuardrailService`, `DefaultToolKey`, `ToolGuardrailChainResolver`
- `langchain4j/src/main/java/dev/langchain4j/service/tool/`: `ToolGuardrailBinding`, `ToolRound`, `ToolService`
- Tests in `langchain4j/src/test/java/dev/langchain4j/service/guardrail/` and `.../service/tool/`,
  `integration-tests-guardrails`
- `docs/docs/tutorials/`

## Key Points

- D20 still holds: callers (`ToolService`, `ToolRound`, frameworks) stay ignorant of the guardrail mechanics.
- Public API changes are limited to the `@Experimental` tool types. Nothing new on `GuardrailService`.
- Test our own contracts: a foreign key fails, a `null` key fails, the key carries the specification and source, the
  AI service API is unchanged (revapi), and the existing end-to-end matrix still passes.
- Never commit `pr-body.md` or `code-review.md`. Never put PLAN.md decision IDs in the source tree.

## Open Questions

- Q1 (owner check), Q3 (cancellation) and Q4 (scanner) are answered: see items 4 and 9.
- Q2 is settled: the factory, on the class side (item 7). The method side is task 18.

## Done When

- [ ] `GenericGuardrailService` is gone, and `ToolGuardrailService` is a standalone `@Experimental` interface with
      `ToolKey`-typed methods and its own builder.
- [ ] `ToolKey` carries the specification and source, and `executeGuardrails` / `executeGuardrailsAsync` no longer take
      them.
- [ ] A `null` key, a key the default service did not bind, and a key of another service instance fail with
      `IllegalArgumentException` (tested).
- [ ] Canceling after the executor hop reaches the tool's future, and a queued tool does not start after a cancel
      (tested).
- [ ] Class-side tool annotations come from `ClassMetadataProviderFactory` only (no reflection fallback), its Javadoc
      says it is asked about tool classes and must return empty for what it does not know, and the framework reuse path
      has its contract test (item 7a).
- [ ] The Compatibility Summary in PLAN.md is rewritten for the reworked PR (hierarchy, deprecations, `resolveMethodKey`,
      Quarkus).
- [ ] `GuardrailService`, its `Builder` and `AbstractGuardrailService` match `main`'s public shape, with no deprecations;
      revapi against 1.24.0 passes with no new justifications.
- [ ] `./mvnw install -pl langchain4j-core,langchain4j` with tests and revapi, and `integration-tests-guardrails`, pass.
- [ ] No whole-file reformatting: the diff only changes formatting on lines the PR changes (spotless is not run, by
      decision; the CI `spotless` job is expected to fail).
- [ ] Docs and Javadoc no longer mention the removed types, the deprecations or the old signatures.
- [x] The user reviewed the changes before any commit.

## Outcome (2026-11-20; reviewed by the user, committed and pushed)

- **Sync:** during the work only fetched (the branch was 49 ahead / 6 behind `upstream/main`); `upstream/main` was merged
  in right after the task 17 commit, then pushed.
- **AI service side (item 6):** `GuardrailService`, `AbstractGuardrailService`, `DefaultGuardrailService` (the `main` name
  is back) and `DefaultGuardrailServiceTests` are restored from the merge base `70dc8fec3`, so their diff is empty. They do
  not delegate to `KeyedGuardrailExecutors` (the task allowed it): a zero diff is easier to review and is `main`'s
  behavior by construction (user, 2026-11-20: leave it; the delegation is done in task 18, with the key resolver). `GuardrailServiceBuilder` implements `GuardrailService.Builder` directly on two
  `GuardrailBuilderSettings` and builds `DefaultGuardrailService` without a key resolver. Removed:
  `GenericGuardrailService`, `AbstractKeyedGuardrailService(Builder)`, `DefaultAiServiceGuardrailService`,
  `GuardrailServiceInspector`, `DefaultAiServiceGuardrailServiceTests`, `GuardrailServiceBuilderHierarchyTest`,
  `AbstractKeyedGuardrailServiceTest`, `GuardrailServiceTargetClassTest`.
- **`resolveMethodKey` off the branch** (item 7 says it is not on this branch any more): `ClassMetadataProviderFactory`
  (core) and `ReflectionBasedClassMetadataProviderFactory` (+ its test) are back to the merge base, plus the new Javadoc;
  `ClassMetadataProviderFactoryTest`, `MethodKeyResolutionTest`, the string-keys module and its `integration-tests/pom.xml`
  line are deleted. Task 18 lists what to recover from `eb0f9c499`.
- **Tool side (items 2-5):** `ToolGuardrailService` has no supertype: `targetClass()`, `bind(..)`, `execute*(ToolKey, ..)`,
  `execute*Async`, `has*(ToolKey)`, `executeGuardrails(key, request, requestParams, tool)` and its async counterpart;
  `ToolKey` has `toolSpecification()`, `toolSource()` (both never null) and a default `toolName()`; `Builder` has the 6
  list setters, 4 varargs defaults and `build()`. `DefaultToolKey` records its owning `DefaultToolGuardrailService`
  (identity), and `DefaultToolGuardrailService` composes two `KeyedGuardrailExecutors`, now lookup-only (the lookup sees
  every key, `null` included). `ToolGuardrailBinding` carries only the service and the key.
- **Fail fast (item 4):** `null`, a hand-written `ToolKey` and a key of a second service built the same way fail every
  lookup with `IllegalArgumentException`; the async ones fail their future (`assertEveryLookupFails` in
  `ToolGuardrailServiceExecuteGuardrailsTest`).
- **Annotations (item 7):** class side through the factory only (no reflection fallback); method side by reflection, with
  a code comment; Javadoc on `ClassMetadataProviderFactory`, `@ToolInputGuardrails`, `@ToolOutputGuardrails`,
  `ToolGuardrailService#bind` and in `_guardrails-extension-points.mdx` (which also loses the `resolveMethodKey`, deprecation
  and sealed-hierarchy text). The `annotationGuardrails` field is moved above the constructor Javadoc. New
  `ToolGuardrailClassMetadataTest` replaces `ToolGuardrailChainResolverAnnotationsTest`.
- **Framework reuse contract (item 7a):** `ToolGuardrailServiceFrameworkReuseTest` (4 tests).
- **Cancellation (item 9, Q3):** `ToolRound.startOn` propagates the cancellation to the tool's future and rechecks both
  the returned future and the round when the task starts. `ToolRoundExecutorHopCancellationTest` (4 tests, paused
  executor).
- **Mutation checks:** old `startOn`, no round recheck, owner check off, `null` accepted, reflection fallback back, factory
  ignored, chain order swapped, rewrite dropped, message not verbatim, key source not used: each fails its tests.
- **Results:** `install -pl langchain4j-core,langchain4j` with tests: core 2083, `langchain4j` 2498 (+597 ITs), 0
  failures; revapi against 1.24.0 (`-Drevapi.oldArtifacts=..:1.24.0`) clean for both, no `revapi.json` diff;
  `integration-tests-guardrails` 366; `langchain4j-agentic` 672. Spotless not run.
- **For the user to decide:** the `DefaultGuardrailService` name (brought back, the task default); removing
  `resolveMethodKey` and the string-keys module here rather than in task 18.
