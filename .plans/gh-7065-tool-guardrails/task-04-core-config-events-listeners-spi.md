# Task 04: Add Configs, Observability Events and Listeners

**Type:** New Code (public API, `langchain4j-core`)

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: none specific.

## Goal

Give tool guardrails the same configuration and observability surface as AI service guardrails: marker
configs, executed events (+ default implementations), and listeners that the registrar dispatches to. The
executor SPI builder factories are in task 05, because they reference the executor builders added there.

## What to Do

1. **Configs** (D15): `ToolInputGuardrailsConfig` / `ToolOutputGuardrailsConfig` (+ `Default*` implementations
   and builders), following `InputGuardrailsConfig` / `DefaultInputGuardrailsConfig`. These are empty markers with
   no `maxRetries`. Add `spi.guardrail.config.*ConfigBuilderFactory` counterparts only if the existing builder
   pattern requires them (it looks up the factory via `ServiceHelper`; mirror whatever `InputGuardrailsConfig.builder()` does).
2. **Events:** `ToolInputGuardrailExecutedEvent` / `ToolOutputGuardrailExecutedEvent` extending
   `GuardrailExecutedEvent<P, R, G>`, with `eventClass()` returning their own class, plus
   `DefaultToolInputGuardrailExecutedEvent` / `DefaultToolOutputGuardrailExecutedEvent` in `observability.event`.
3. **Listeners:** `ToolInputGuardrailExecutedListener` / `ToolOutputGuardrailExecutedListener`
   (`non-sealed`, extending `GuardrailExecutedListener`), added to its `permits`. Verify that
   `DefaultAiServiceListenerRegistrar` dispatches them, since it dispatches by exact `eventClass()` (line ~119).
   If it needs a mapping from listener type to event class, add it.
4. _(Moved to task 05: the executor SPI builder factories.)_
5. Verify that `AiServiceInvocationSummaryEvent` automatically includes the new events (it is documented as
   "all the events fired for it"). If it filters by type, extend it.
6. Unit tests: event builders and defaults, registrar dispatch to the new listener types (and **not** to the
   AI-service guardrail listeners), and inclusion in the summary event.

## Files/Areas

- `langchain4j-core/src/main/java/dev/langchain4j/guardrail/config/` (new configs)
- `langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/` (new event interfaces)
- `langchain4j-core/src/main/java/dev/langchain4j/observability/event/` (new defaults)
- `langchain4j-core/src/main/java/dev/langchain4j/observability/api/listener/GuardrailExecutedListener.java`
  (`permits`) + new listeners
- `langchain4j-core/src/main/java/dev/langchain4j/observability/api/DefaultAiServiceListenerRegistrar.java`
- `langchain4j-core/src/main/java/dev/langchain4j/observability/api/event/AiServiceInvocationSummaryEvent.java`

## Key Points

- Listener sealing: `GuardrailExecutedListener` is sealed and lives in `observability.api.listener`, so the new
  listeners must be in that same package.
- Event naming must not collide with the AI service guardrail events. A listener for `InputGuardrailExecutedEvent`
  must **not** receive tool guardrail events.
- Adding permitted subtypes can break exhaustive `switch`es in user code. Note this for task 12.

## Done When

- [x] Configs, events, defaults and listeners exist and are permitted.
- [x] Registrar dispatch is verified by a test for both new listener types.
- [x] Summary event includes the new events (verified by a test).
- [x] `langchain4j-core` compiles with no new warnings.
