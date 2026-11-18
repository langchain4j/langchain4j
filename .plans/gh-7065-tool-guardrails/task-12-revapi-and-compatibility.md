# Task 12: Revapi Justifications and Compatibility Review

**Type:** Build / API compatibility

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: all of them (the revapi baseline moves with `main`).

## Goal

Make the revapi check pass for `langchain4j-core`, `langchain4j`, `langchain4j-mcp` and `langchain4j-agentic`, with accurate
justifications, and produce a compatibility summary for the PR body.

## What to Do

1. Run the revapi check for the affected modules (find the exact invocation in `langchain4j-parent/pom.xml`; the
   revapi plugin runs in the standard build), and collect every reported difference.
2. Expected categories (verify; don't assume):
   - new permitted subtypes on sealed types (`GuardrailRequest`, `GuardrailResult`, `GuardrailResult.Failure`,
     `GuardrailException`, `GuardrailExecutor` / `AbstractGuardrailExecutor` / its builder,
     `GuardrailExecutedListener`, `GuardrailExecutorBuilderFactory`). Revapi doesn't model sealing; justify these
     as "sealed; only permits are added inside langchain4j-core".
   - the new protected `rewriteRequest` hook on `@Internal` `AbstractGuardrailExecutor`
   - new `CompensationReason` enum constants (the enum is `@Experimental`; say so in the justification)
   - new methods on `AiServices`, `AiServiceTool` / its builder, `AgentBuilder` (`langchain4j-agentic`), and
     possibly `ToolService` overloads
3. Add justifications to `langchain4j-core/revapi.json`, `langchain4j/revapi.json` and (if needed)
   `langchain4j-mcp/revapi.json` / `langchain4j-agentic/revapi.json`, following the existing wording style (see the `GuardrailExecutor::executeAsync`
   and `OutputGuardrailExecutor::composeResult` entries).
4. Write a short **compatibility summary** in PLAN.md for the PR body:
   - source-compatible for normal use; **may break exhaustive `switch`es** over the extended sealed types and over
     `CompensationReason` in user code (none exist in this repo; verify with `grep`)
   - no changes to `@InputGuardrails` / `@OutputGuardrails` / `InputGuardrail` / `OutputGuardrail`
   - behavior is unchanged when no tool guardrails are configured
   - everything new is `@Experimental` (D19), **except** `GenericGuardrailService` (+ its `Builder`), which the stable
     `GuardrailService` extends, and `ClassMetadataProviderFactory#resolveMethodKey` (user decision, 2026-11-17)
   - the guardrail service hierarchy (task 06a): `GenericGuardrailService` / `GenericGuardrailService.Builder` are new
     and sealed; `GuardrailService` / `GuardrailService.Builder` became `non-sealed` and redeclare their methods for
     binary compatibility (removing a redeclaration fails revapi with `java.method.*TypeErasureChanged`, verified
     2026-11-17); `AbstractGuardrailService` and `GuardrailService#aiServiceClass()` are deprecated; frameworks that
     subclass `AbstractGuardrailService` or implement `GuardrailService.Builder` (CDI, Micronaut) keep working
5. Check whether Quarkus-relevant SPI types (`*ExecutorBuilderFactory`) changed in a way that affects
   quarkus-langchain4j, and note it for [quarkiverse/quarkus-langchain4j#3162](https://github.com/quarkiverse/quarkus-langchain4j/issues/3162).

## Files/Areas

- `langchain4j-core/revapi.json`, `langchain4j/revapi.json`, `langchain4j-mcp/revapi.json`,
  `langchain4j-agentic/revapi.json`
- `langchain4j-parent/pom.xml` (revapi configuration)

## Key Points

- Justify only real differences. Do not add blanket ignores.
- If revapi flags something unexpected (e.g. a changed public signature), fix the code rather than justifying it.

## Done When

- [ ] Revapi passes for all affected modules.
- [ ] Every justification is specific and accurate.
- [ ] The compatibility summary is recorded in PLAN.md.
