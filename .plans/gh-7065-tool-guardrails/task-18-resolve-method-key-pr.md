# Task 18: Move `resolveMethodKey` to Its Own PR (1.25.0)

**Type:** Separate PR (`langchain4j-core`, `langchain4j`), branched from `upstream/main`

> **Why:** @dliubarskyi asked for it in his
> [design review](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7037036391): it fixes AI service
> guardrails for `ClassMetadataProviderFactory`s with their own method keys (including the async case in
> [micronaut-projects/micronaut-langchain4j#455](https://github.com/micronaut-projects/micronaut-langchain4j/issues/455)),
> so it deserves its own review, for example whether the parameter should be a `Method`.
>
> **Order:** do it after task 17, so it is clear what PR #7202 still needs from it (the tool method annotation lookup,
> see task 17 item 7).

## Goal

A small, self-contained PR that adds `ClassMetadataProviderFactory#resolveMethodKey` and uses it in the default
`GuardrailService`, blocking and non-blocking, so that a framework whose method keys are not `Method`s needs only the
`ClassMetadataProviderFactory` and `ClassInstanceFactory` SPIs, with no builder copy and no subclass.

## What to Do

1. Branch from `upstream/main` (not from the tool guardrails branch).
2. Bring over from PR #7202 (do not cherry-pick whole commits; they mix in tool guardrail work). Task 17 took all of
   it off the branch, so take it from `eb0f9c499` (the last commit before task 17), for example with
   `git show eb0f9c499:<path>`:
   - `ClassMetadataProviderFactory#resolveMethodKey` (core), with @dliubarskyi's `7e4d448cb` changes: default returns
     `null`, the reflection-based factory returns the `Method`, and the lookup tries the key as is first;
   - the key resolver of the AI service guardrail lookup (what `KeyedGuardrailExecutors` did at `eb0f9c499`: resolve
     once per key, cache it, never call it with `null`), applied to the blocking and the async paths. Since task 17 the
     AI service side is `main`'s `AbstractGuardrailService` / `DefaultGuardrailService` again, and
     `KeyedGuardrailExecutors` only does the lookup of the tool keys, so the resolver goes into the AI service side. Do it by moving `AbstractGuardrailService` onto `KeyedGuardrailExecutors` (map mode plus the resolver; user, 2026-11-20), keeping its protected constructor and package-private test helpers; note the `startSafely` change of the async methods (a synchronous throw becomes a failed future);
   - the override in `ReflectionBasedClassMetadataProviderFactory` and its test case in
     `ReflectionBasedClassMetadataProviderFactoryTests`;
   - the `integration-tests-class-metadata-provider-string-keys` module (and its line in `integration-tests/pom.xml`),
     `StringKeyedGuardrailsTests` and `MethodKeyResolutionTest`;
   - the core `ClassMetadataProviderFactoryTest` (also needed for the jacoco gate of `dev.langchain4j.spi.classloading`
     once the interface has a default method again);
   - the `resolveMethodKey` bullet of the `ClassMetadataProviderFactory` row in `_guardrails-extension-points.mdx`.
3. **Decide the signature with the reviewer:** `resolveMethodKey(Object method)` vs `resolveMethodKey(Method method)`.
   Propose `Method` if nothing passes anything else (check every caller).
4. **The method side of the tool guardrail annotations (user decision, 2026-11-20, agreeing with Dmytro).** Task 17
   makes the class side go through `ClassMetadataProviderFactory` only; the method side was left to this PR, and PR
   #7202 reads it by reflection meanwhile. Here, route it through the factory too: for a tool core discovers (a
   `DefaultToolExecutor`, which has a `Method`), `resolveMethodKey(method)`, then `getAnnotation(key, annotationType)`
   when there is a key. Settle with the reviewer what happens when there is no key (`null`): reflection, or nothing.
   `7e4d448cb`'s `ClassCastException` came from the tool path calling the identity `resolveMethodKey` with a factory whose
   keys are not `Method`s, so test that case (the string-keys module). Also decide whether the default returns `null`
   (his change) or the method (the original). Tools of a framework's own tool loop (Quarkus) have no `Method` and stay on
   `AiServiceTool` guardrails (PLAN.md → "Quarkus reuse path").
5. **Deprecating `AbstractGuardrailService`:** only if the reviewer wants it in this PR (it is what `resolveMethodKey`
   makes unnecessary). He pointed out that the deprecation gives Micronaut and CDI warnings they cannot get rid of
   before 2.0, so the default is **no deprecation**, and the Javadoc points to the SPIs instead.
6. Docs: `_guardrails-extension-points.mdx` and `guardrails.md` (the "framework with its own method keys" part).
7. If PR #7202 still needs `resolveMethodKey` for the tool method annotations, update it once this PR merges (merge
   `upstream/main` into the tool guardrails branch).

## Key Points

- `resolveMethodKey` must return the same key for the same method: the default service caches it per key.
- Callers pass it a `Method` today; a factory may also be called with one of its own keys and must return it unchanged
  (`7e4d448cb` Javadoc).
- Keep the CDI and Micronaut shapes working: a subclass of `AbstractGuardrailService` that overrides the four lookup
  methods must behave as before.

## Done When

**Status (2026-11-20):** PR open ([#7214](https://github.com/langchain4j/langchain4j/pull/7214)), awaiting @dliubarskyi's review.
Open: his sign-off on the design choices (`Method` parameter, `Optional` return, empty default), and any review
changes. Spotless is waived for #7214 as for #7202 (user, 2026-11-20): not run, CI spotless job expected red.

- [x] A separate PR from `upstream/main` adds `resolveMethodKey` and uses it on the blocking and async paths, with the
      string-keys integration module and the unit tests.
- [ ] The parameter type and the default of `resolveMethodKey` are settled with the reviewer.
- [ ] Revapi passes; root `./mvnw -Pspotless validate` passes. (Revapi passes; spotless waived by the user.)
- [x] The user reviewed the changes and approved the PR text before it is opened.

## Outcome (2026-11-20; implemented, UNCOMMITTED, awaiting the user's review)

- **Where:** branch `resolve-method-key` off `upstream/main` (`a30ea36b4`), in a separate worktree, so the tool
  guardrails branch stays checked out.
- **SPI (proposal for the reviewer):** `Optional<MethodKey> resolveMethodKey(Method method)`, default `Optional.empty()`
  (the `Method` is used as the key); `ReflectionBasedClassMetadataProviderFactory` returns `Optional.of(method)`.
  `Method` because every caller passes a `Method` (`DefaultAiServices`; streaming via
  `AiServiceTokenStreamParameters#methodKey`); a framework's own keys are used as is, without the resolver. Quarkus is
  that case (keys are `AiServiceMethodCreateInfo`, passed by its own invocation path), so it never reaches the
  resolver; the PR text links the call sites (user, 2026-11-20).
  `Optional` matches the `getAnnotation` methods of the same SPI.
- **AI service side:** `AbstractGuardrailService` delegates to a new package-private `KeyedGuardrailExecutors` (map mode
  plus resolver: key as is first, then, for a `Method` only, the resolver once per method, cached). Protected
  3-arg constructor unchanged (no resolver); new package-private 4-arg constructor used by `DefaultGuardrailService`,
  which `GuardrailServiceBuilder` builds with `factory::resolveMethodKey`. Test helpers kept. The own `has*` caches are
  gone. Async lookups go through `failOnThrow` (a synchronous throw becomes a failed future).
- **Tests:** `ClassMetadataProviderFactoryTest` (core), `ReflectionBasedClassMetadataProviderFactoryTests#methodIsItsOwnKey`,
  `KeyedGuardrailExecutorsTest` (13), `MethodKeyResolutionTest` (6, incl. the protected constructor), string-keys module
  `StringKeyedGuardrailsTests` (6, incl. a new `TokenStream` case).
- **Results:** core 2038, `langchain4j` 2315 unit tests, 0 failures (`-DskipITs`); string-keys 6, Spring class metadata 2,
  `integration-tests-guardrails` 116, all green; revapi against 1.24.0 clean for both modules. Spotless not run.
- **Docs:** `guardrails.md` extension-points row of `ClassMetadataProviderFactory` (the `.mdx` exists only on #7202).
- **Not done here:** item 4 (method side of the tool guardrail annotations) cannot be done from `upstream/main`, which has
  no tool guardrails: it goes into #7202 after this PR merges (item 7). Item 5: no deprecation.
- **Opened (2026-11-20, after the user's review):** commit `ac7fbea29` on `resolve-method-key`, pushed to the fork;
  PR [#7214](https://github.com/langchain4j/langchain4j/pull/7214). User agreed on decisions 2 (`Optional`) and 3
  (empty default). Item 4 is now issue [#7215](https://github.com/langchain4j/langchain4j/issues/7215) (depends on
  #7202 and #7214; open question: reflection fallback or nothing when there is no key). #7202's body was updated for
  the rework (Edit note) and points to both; review-ready comment posted to @dliubarskyi (issuecomment-7043968768).
  Spotless not run, so #7214's spotless job may be red too.
