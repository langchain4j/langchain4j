# Task 15: Follow Up With Micronaut (after the PR exists)

**Type:** Communication (no code in this repo)

> **Do this only after the PR is open** (task 14), so the issue can link to it. Nothing is sent before the user has
> reviewed and approved the text. The `langchain4j-cdi` issue ([langchain4j/langchain4j-cdi#339](https://github.com/langchain4j/langchain4j-cdi/issues/339)) is already filed and is
> left alone: no follow-up comment (user decision, 2026-11-16).

## Goal

Tell `micronaut-langchain4j` what we did in the guardrail hierarchy, and how it affects them, as an FYI with a link to
the PR. We are not asking them to do anything urgently.

## What to Do

1. Re-read PLAN.md (Task Outcomes for 06a) and the final state of the PR, so that the text matches what was actually
   merged or proposed, not what was planned.
2. Draft an issue for `micronaut-projects/micronaut-langchain4j` (draft first in this folder, get the user's approval,
   then file it with `gh issue create`). Content, in US English and with no private details:
   - What changed in `langchain4j` (link to the PR): the guardrail service hierarchy now has a generic base
     (`GenericGuardrailService`, internal `AbstractKeyedGuardrailService`); `AbstractGuardrailService` and
     `GuardrailService#aiServiceClass()` are deprecated; new `ClassMetadataProviderFactory#resolveMethodKey`.
   - How it relates to what Micronaut does today (verified on 2026-11-16 by reading
     `MicronautGuardrailServiceBuilder`): method keys are strings built from `ExecutableMethod`, annotations come from
     Micronaut's `AnnotationMetadata`, and `MicronautGuardrailService` subclasses `AbstractGuardrailService` to translate
     the `java.lang.reflect.Method` passed at invocation time into those keys (overriding four methods).
   - Compatibility: a subclass of that shape keeps working unchanged on the new jars for the blocking path (verified
     with a Micronaut-shaped reproduction compiled against 1.23.0); it will get a deprecation warning.
   - The migration path: `resolveMethodKey` lets a `ClassMetadataProviderFactory` own that translation, so the default
     builder and service work with string keys, with no builder copy and no subclass. Mention the end-to-end example in
     `integration-tests-class-metadata-provider-string-keys`. Say that `resolveMethodKey` must return the same key for
     the same method, since the default service caches it per key. `GenericGuardrailService` and its `Builder` are
     sealed, but `GuardrailService`, `GuardrailService.Builder` and `AbstractGuardrailService` stay open, so nothing
     they implement or extend today is affected. Note the open question they would have to answer: a
     `ServiceLoader`-created factory has no injected `BeanContext` (the Spring integration test uses a static holder).
   - The aside: `MicronautGuardrailService` does not override the async variants (`executeInputGuardrailsAsync` and the
     output counterpart), so guardrails look to be skipped in the non-blocking modes (reproduced with a reduced shape,
     not with real Micronaut, so word it as "looks like"). `resolveMethodKey` fixes this as a side effect.
   - Tone: FYI, no deadline, no offer to write a PR (user decision, 2026-11-19: no Micronaut expertise); ask them to open a LangChain4j issue if anything does not work.
3. After filing, record the link in PLAN.md.

## Done When

- [x] The user approved the text.
- [x] The issue is filed on `micronaut-projects/micronaut-langchain4j` and linked in PLAN.md: [micronaut-projects/micronaut-langchain4j#455](https://github.com/micronaut-projects/micronaut-langchain4j/issues/455).
