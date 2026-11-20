# Task 19: Keep the Micronaut, CDI and Quarkus Issues Up to Date

**Type:** Communication (no code in this repo)

> **Why:** @dliubarskyi's [design review](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7037036391)
> asked for short follow-ups, since the Micronaut and CDI issues mention the deprecations that task 17 removes. We
> committed to it in [our reply](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7040595689).
>
> **How (user, 2026-11-20):** edit the existing issue / PR text **in place**, with an "_Edit (date): ..._" note, instead
> of posting reply comments. Nothing is saved before the user has reviewed and approved the new text.

## Status (2026-11-20): all edited in place

- **[micronaut-projects/micronaut-langchain4j#455](https://github.com/micronaut-projects/micronaut-langchain4j/issues/455)**:
  title and body rewritten. New title: "FYI: LangChain4j tool guardrails PR and Micronaut (no deprecations after review;
  `resolveMethodKey` moving to its own PR)". The body says `GuardrailService` / its `Builder` / `AbstractGuardrailService`
  stay as they are (no deprecations), tool guardrails get their own `@Experimental` `ToolGuardrailService`, and
  `resolveMethodKey` moves to its own PR; the migration path is "optional, once that PR lands" (signature may change);
  the async gap stays ("looks like"; overriding the two `*Async` methods closes it today). New section: Micronaut passes
  its tools to `AiServices.tools(..)`, so tool guardrails apply, but it registers no `ClassInstanceFactory`, so the
  guardrail classes of `@ToolInputGuardrails` / `@ToolOutputGuardrails` are created with their no-arg constructor, not as
  Micronaut beans (verified in `MicronautGuardrailServiceBuilder` and `AiServiceFactory`, not run); the fixes are a
  `BeanContext`-backed `ClassInstanceFactory` or passing bean instances.
- **[langchain4j/langchain4j-cdi#339](https://github.com/langchain4j/langchain4j-cdi/issues/339)** (issue body): the
  "will most likely deprecate" wording and the heads-up section replaced: no deprecation in 1.x, tool guardrails get
  their own `ToolGuardrailService`, a single contract is for 2.0; a `ClassInstanceFactory` also makes tool guardrail
  classes CDI beans.
- **[langchain4j/langchain4j-cdi#340](https://github.com/langchain4j/langchain4j-cdi/pull/340)** (the user's PR that
  switches CDI to a `ClassInstanceFactory`; description): tool guardrail classes will be CDI beans with no change; the
  deprecation heads-up replaced by an edit note (cleanup, not a migration).
- **[quarkiverse/quarkus-langchain4j#3162](https://github.com/quarkiverse/quarkus-langchain4j/issues/3162)**:
  [investigation notes](https://github.com/quarkiverse/quarkus-langchain4j/issues/3162#issuecomment-7041587374) posted
  at the user's request, framed as notes from this work with a caveat that API names may change, then edited in place
  after the factory decision (task 17 item 7): Quarkus's `ClassMetadataProviderFactory` must return empty for (or
  answer) the tool guardrail annotations.

## What Is Left

1. Once task 17 is pushed, re-check every API name in the four texts against the pushed code
   (`executeGuardrails(key, request, requestParams, tool)`, `bind(null, tool)`, class-side tool annotations through the
   factory, the `AiServices` tool guardrail setters) and edit in place again if anything changed.
2. Once task 18's PR is open, edit #455 (and #339 if useful) in place to link it.
3. Record any new link in PLAN.md.

## Done When

- [x] The user approved all texts (2026-11-20).
- [x] #455, #339, #340 and #3162 are updated in place, and their links are recorded here and in PLAN.md.
- [ ] Re-checked after task 17 is pushed, and #455 links task 18's PR.
