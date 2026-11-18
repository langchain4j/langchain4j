# Task 06b: Annotations, `AiServices` / `AiServiceTool` / `AgentBuilder` Config, and Per-Tool Guardrail Resolution

**Type:** New Code (public API, `langchain4j` + `langchain4j-agentic`)

> **Status (2026-11-17): reworked on top of 06a, uncommitted, awaiting review** (see PLAN.md → Task Outcomes). Earlier status (2026-11-16): implemented, then parked. The work is in `git stash`
> (`stash@{0}`, message "task-06b: ..."), taken before task 06a so the 06a refactoring could be reviewed on its own.
> Order: do task 06a first, then `git stash pop` and rework this task on top of it (see "Adjustments after the split"
> at the end). Before popping, discard the four spotless import-order fixes in `langchain4j-core` that are in the
> working tree (`git checkout -- langchain4j-core`), because the stash contains the same fixes. This task **depends on
> task 06a**.
>
> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: #7088 (guardrail precedence Javadoc, which this task mirrors; it replaced our #7109).
> D6 is **settled** (run all, additive; @dliubarskyi in
> https://github.com/langchain4j/langchain4j/discussions/7030#discussioncomment-19659065). Tool guardrails deliberately
> resolve **differently** from AI service guardrails, so do not copy #7088's "not combined" wording.

## Goal

Let users attach tool guardrails through every supported surface (D1, D10), and resolve the effective
input and output executors **per tool name** with the rule settled in D6: **every guardrail that applies runs, as
one chain, in the order builder (global) → class annotation → method annotation / `AiServiceTool`**, resolved
separately for input and output. No level replaces another.

## What to Do

1. **Annotations** in `dev.langchain4j.service.guardrail`:
   ```java
   @Target({TYPE, METHOD}) @Retention(RUNTIME) @Documented?
   public @interface ToolInputGuardrails  { Class<? extends ToolInputGuardrail>[]  value(); }
   public @interface ToolOutputGuardrails { Class<? extends ToolOutputGuardrail>[] value(); }
   ```
   Mirror the meta-annotations of `@InputGuardrails`. Simple names match Quarkus, so migration there is mostly
   an import change.
2. **Global config on `AiServices`**, mirroring `inputGuardrails(..)` / `inputGuardrailClasses(..)` (List and
   varargs variants): `toolInputGuardrails`, `toolInputGuardrailClasses`, `toolOutputGuardrails`,
   `toolOutputGuardrailClasses`. Add `toolInputGuardrailsConfig` / `toolOutputGuardrailsConfig` only if they make
   sense for empty marker configs (D15); ask the user if unsure. Javadoc must state that these guardrails apply to
   **every** tool and run **first**, followed by the tool's class and method (or `AiServiceTool`) guardrails, and
   that this **differs** from AI service guardrails (where the builder replaces the annotations). Mention that a
   global guardrail can skip tools via `toolName()` / `toolSource()`.
3. **`AiServiceTool.Builder`**: add `toolInputGuardrails(..)` / `toolOutputGuardrails(..)` (instances; classes
   optional) and `toolSource(ToolSource)`, plus accessors. Update `equals` / `hashCode` / `toString` / `toBuilder`.
   They occupy the method slot of the chain (after the builder and class guardrails; D10).
4. **Internal `ToolGuardrailService`** (`@Internal`, in `service.tool` or `service.guardrail`):
   - Resolves, per tool name and separately for input and output, the effective `ToolInputGuardrailExecutor` /
     `ToolOutputGuardrailExecutor`:
     the concatenation, in this order, of
     1. the `AiServices` builder tool guardrails (instances and classes, combined the same way
        `GuardrailServiceBuilder` combines `inputGuardrails(..)` and `inputGuardrailClasses(..)`)
     2. the class annotation (the tool object's class; check supertypes the same way `@Tool` discovery does, and
        report how inherited / overriding class annotations are handled)
     3. the method annotation (via `DefaultToolExecutor.originalMethod()`), then the `AiServiceTool` guardrails
     If the result is empty: no executor (fast path, zero overhead). `null` / empty builder lists simply mean no
     global guardrails. **Deduplication (user decision, 2026-11-16):** the same guardrail **class** from class-based means (annotations, `*GuardrailClasses(..)`) at several levels runs once per tool, keeping the first occurrence; **instances** are deduplicated only by reference (no `equals()` contract), since two instances of one class can differ in constructor state; a builder instance plus an annotation class naming the same class both run. The key is one method (config is chain-wide today; per-level config attributes would have to become part of it). Document it.
   - Instantiates guardrail classes via `ClassInstanceFactory` (reuse `GuardrailServiceBuilder`'s instance lookup
     logic rather than duplicating it).
   - Caches static tools (`@Tool` objects, `tools(Map)`, `tools(List<AiServiceTool>)`) at configuration time, and
     resolves dynamic tools (`ToolProvider` / MCP via `ToolProviderResult.aiServiceTools()`, including their `toolSource()`) per invocation,
     carried in `ToolServiceContext`.
   - Records the `ToolSource` per tool name: `local(method)` for `@Tool` methods; the `AiServiceTool`'s source if
     set; otherwise `programmatic()`.
5. **`AgentBuilder`** (`langchain4j-agentic`, D10): add `toolInputGuardrails(..)`, `toolInputGuardrailClasses(..)`,
   `toolOutputGuardrails(..)` and `toolOutputGuardrailClasses(..)`, shaped like the existing guardrail setters
   (~lines 604-663) and passed to `AiServices` where the existing ones are (~lines 333-345). Same ordering note in
   the Javadoc (run first, in addition to per-tool guardrails). Check whether other agentic builders that delegate to `AgentBuilder` (declarative agent
   annotations, supervisor / planner builders) expose guardrail config and need the same; report what you find.
6. If both a method annotation **and** `AiServiceTool` guardrails exist for the same tool (normally impossible,
   because the framework builds `AiServiceTool` for `@Tool` methods), both run, method annotation first, following
   the "every guardrail that applies runs" rule. Document it.
7. Edge case: **an AI service proxy used as a tool**. `@InputGuardrails` on it are AI service guardrails and
   `@ToolInputGuardrails` are tool guardrails. Each annotation has exactly one meaning, so no partitioning is needed.
   Add a test.
8. Unit tests (`langchain4j` module, plus one in `langchain4j-agentic`):
   - resolution matrix with the **exact chain order**: builder only / class only / method only / class+method
     (class, then method) / builder+method / builder+class+method / builder+`AiServiceTool` / none (no executor)
   - input and output are resolved independently (e.g. builder sets only output; input comes only from annotations)
   - the same guardrail class at several levels runs once (first position kept); two distinct instances of one class both run; the same instance twice runs once; a builder instance and an annotation class naming the same class both run
   - rewrites chain across levels (a builder rewrite is what the class / method guardrails see)
   - class instantiation via the SPI factory; caching (the same executor instance per tool)
   - `ToolSource` recorded correctly for each tool kind
   - a `null` or empty global list means no global guardrails
   - @PSchmitz-Valckenberg's scenario (discussioncomment-19603638): a fetching `@Tool` with
     `@ToolOutputGuardrails(FormatCheck.class)` plus a global injection check on the builder → both run, global first
   - @dliubarskyi's scenario (discussioncomment-19659065): `WebTools` with a class-level injection check and a
     method-level size limit on `fetch()` → both run for `fetch`, class first; other methods get only the class one
   - `langchain4j-agentic`: tool guardrails set on `AgentBuilder` reach the agent's tools

## Files/Areas

- New: `langchain4j/src/main/java/dev/langchain4j/service/guardrail/ToolInputGuardrails.java`, `ToolOutputGuardrails.java`
- `langchain4j/src/main/java/dev/langchain4j/service/AiServices.java` (+ `AiServiceContext` if config lives there)
- `langchain4j/src/main/java/dev/langchain4j/service/tool/AiServiceTool.java`
- `langchain4j/src/main/java/dev/langchain4j/service/tool/ToolService.java` (`tools(..)`, `toolProvider(..)`, tool
  discovery), `ToolServiceContext.java`
- New internal `ToolGuardrailService`
- `langchain4j-agentic/src/main/java/dev/langchain4j/agentic/agent/AgentBuilder.java`
- Reference: `GuardrailServiceBuilder.java:170-305`, `DefaultToolExecutor.java:84`

## Key Points

- Do not change `GuardrailService` / `GuardrailServiceBuilder` behavior. Tools are keyed by name and can be
  dynamic, so they need their own small service (spike §6).
- Tool names can be remapped (MCP `toolNameMapper`); key by the **final** name the LLM sees, which is the same key
  `toolExecutors` uses.
- Keep the zero-guardrails path allocation-free and unchanged in behavior.

## Done When

- [ ] Annotations, `AiServices` methods, `AiServiceTool` setters and `AgentBuilder` setters exist with correct
      Javadoc, all `@Experimental` (D19).
- [ ] `ToolGuardrailService` resolves one chain per tool in the D6 order (builder → class → method /
      `AiServiceTool`); covered by the resolution-matrix tests.
- [ ] `ToolSource` is recorded for every tool kind.
- [ ] The AI-service-proxy-as-tool case is covered by a test.
- [ ] `langchain4j` and `langchain4j-agentic` compile and their tests pass.

## Adjustments after the split (decided with the user, 2026-11-16)

What exists in the stash was written before the decision to make guardrails a reusable black box. Rework it as follows
on top of task 06a:

1. **`ToolGuardrailService` becomes an `@Experimental` interface extending the `GenericGuardrailService<ToolInputGuardrailRequest,
   ToolInputGuardrailResult, ToolOutputGuardrailRequest, ToolOutputGuardrailResult>` created in task 06a** (a sibling of
   `GuardrailService`: both extend the generic parent and split below it; it is not a subtype of `GuardrailService`,
   since the request and result types differ). **`targetClass()` is the AI service class** (user decision, 2026-11-16,
   option 1), so `ToolGuardrailService.builder(Class<?> aiServiceClass)` takes it, like
   `GuardrailService.builder(aiServiceClass)`, and `AiServices` passes its AI service class: `bind(toolObject, AiServiceTool)` returns an opaque key (today's
   `ToolGuardrailBinding`); `executeInput(key, request)` / `executeOutput(key, request)` plus async variants; a
   non-fatal failure is **returned**, never thrown (D13). The current class becomes `DefaultToolGuardrailService`,
   built on the generic base from task 06a. `ToolService` (tasks 07 and 08) depends on the interface, not on the
   executors. Add `ToolGuardrailService.Builder`, and have the `AiServices` / `AgentBuilder` setters configure it, like
   `GuardrailService.Builder` for AI services.
2. **Downstream SPI factory: INCLUDED in this PR (user decision, 2026-11-16).** Analogous to the existing
   `GuardrailServiceBuilderFactory` (`dev.langchain4j.service.guardrail.spi`): a `ToolGuardrailServiceBuilderFactory`
   with `ToolGuardrailService.Builder getBuilder(Class<?> aiServiceClass)`, looked up with `ServiceLoader` in
   `ToolGuardrailService.builder(aiServiceClass)` (first one found, falling back to the default builder),
   `@Experimental`. It takes the AI service class because `GenericGuardrailService.targetClass()` is the AI service
   class for tool guardrails too. Add a test that registers a custom factory through
   `META-INF/services` in the test resources and checks that `AiServices` uses its service, plus the revapi check.
3. **Annotations go through `ClassMetadataProviderFactory`** (done in the stash; keep): class-level through
   `factory.getAnnotation(Class, ..)` over the class hierarchy; method-level by finding the factory's key among
   `getNonStaticMethodsOnClass(declaringClass)` (the AI service way), **falling back to `Method.getAnnotation`** for
   methods the factory does not list (the default factory uses `getMethods()`, public only, and `@Tool` methods can have
   any visibility, so without the fallback their guardrails would be silently ignored).
4. **`tools(Map<ToolSpecification, ToolExecutor>)` goes through the same binding path** (an `AiServiceTool` is built
   from the pair), so a `DefaultToolExecutor`'s method annotations are honored (done in the stash; keep).
5. **Config setters** `toolInputGuardrailsConfig` / `toolOutputGuardrailsConfig` on `ToolService`, `AiServices` and
   `AgentBuilder` (done in the stash; keep), passed to every tool's executor.
6. **Deduplication** (done in the stash; keep): the same class from annotations / `*GuardrailClasses(..)` at several
   levels runs once; instances only by reference; a builder instance and an annotation class both run. The key lives in
   one method (internal `GuardrailEntry` records whether a guardrail came from a class).
7. Revapi: four `externalClassExposedInAPI` justifications in `langchain4j-agentic/revapi.json` (the two guardrail
   interfaces and the two configs); re-check after the rework.
8. **Package and sealing (decided 2026-11-16):** `AbstractKeyedGuardrailService` is sealed (`permits AbstractGuardrailService,
   DefaultAiServiceGuardrailService`). `ToolGuardrailService`, `ToolGuardrailService.Builder`, the SPI factory and
   `DefaultToolGuardrailService` therefore go in `dev.langchain4j.service.guardrail` (next to `@ToolInputGuardrails` /
   `@ToolOutputGuardrails`, which are already planned there), and `DefaultToolGuardrailService` (final, public,
   `@Experimental`) must be added to the `permits` clause and to `AbstractKeyedGuardrailServiceTest`. The per-tool binding
   and annotation resolution (`ToolGuardrailBinding`, resolution helpers, `GuardrailEntry`) can stay in `service.tool`.
   **Update 2026-11-17:** `GenericGuardrailService` and `GenericGuardrailService.Builder` are sealed too, so
   `ToolGuardrailService` must be added to `permits` of `GenericGuardrailService` and `ToolGuardrailService.Builder` to
   `permits` of `GenericGuardrailService.Builder` (both `non-sealed`, since the downstream SPI factory of item 2 lets
   frameworks implement the builder), and the tool builder implementation to `permits` of
   `AbstractKeyedGuardrailServiceBuilder`; update the tests that pin these sets (`AbstractKeyedGuardrailServiceTest`,
   `GuardrailServiceBuilderHierarchyTest`). The builder generics are now
   `GenericGuardrailService.Builder<ToolInputGuardrailRequest, ToolInputGuardrailResult, ToolOutputGuardrailRequest,
   ToolOutputGuardrailResult, ToolGuardrailService, ToolInputGuardrail, ToolOutputGuardrail, ToolInputGuardrailsConfig,
   ToolOutputGuardrailsConfig, ToolGuardrailService.Builder>`, and the tool builder implementation extends
   `AbstractKeyedGuardrailServiceBuilder` with the request and result types first and `ToolInputGuardrailExecutor` /
   `ToolOutputGuardrailExecutor` as `IE` / `OE`. `KeyedGuardrailExecutors` and `GuardrailBuilderSettings` are
   package-private now, so code in `service.tool` cannot use them.
