# Task 13: Documentation

**Type:** Documentation

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: [#7088](https://github.com/langchain4j/langchain4j/pull/7088) (`guardrails.md`; it replaced our [#7109](https://github.com/langchain4j/langchain4j/pull/7109)) and [#7106](https://github.com/langchain4j/langchain4j/pull/7106) (`non-blocking.md`).

## Goal

Document tool guardrails fully and update every affected doc page and Javadoc, including all items in
PLAN.md "Documentation Must-Haves".

## What to Do

1. **`docs/docs/tutorials/guardrails.md`**: add a top-level `## Tool Guardrails` section (or a new page
   `tool-guardrails.md` linked from here and from the sidebar; ask the user which they prefer). Cover:
   - what tool guardrails are and the indirect-prompt-injection motivation
   - `ToolInputGuardrail` / `ToolOutputGuardrail`, the request contents (including `toolSource()`, `isError()`,
     all result contents), and outcomes (success / rewrite / failure / fatal) in a table like the existing
     "Outcomes" tables
   - non-fatal failure → error tool result with the message **verbatim**; the input-rejected tool is not executed
   - fatal → `Tool*GuardrailException`; `onError` / failed future on non-blocking paths; bypasses
     `ToolExecutionErrorHandler`
   - output guardrails also run on **error results**; opt out via `isError()`. `successWith(String)` keeps the
     `isError` of the result it rewrites, so sanitizing an error result never hides a failure; only
     `successWith(List<Content>, boolean)` can change it
   - multi-content results and content-level rewrites
   - input rewrites: the tool runs with the rewritten arguments, and callbacks and events report them (D18)
   - the API is `@Experimental` (D19)
   - declaring: `@ToolInputGuardrails` / `@ToolOutputGuardrails` (method, class), `AiServices` global,
     `AgentBuilder` global, `AiServiceTool` (MCP tools are guarded globally or by routing on `ToolSource`; per-server config is a follow-up)
   - **resolution order** (D6): every guardrail that applies runs, as one chain: builder (global) → class
     annotation → method annotation / `AiServiceTool`. A prominent callout that this **differs from AI service
     guardrails** (where the builder replaces the annotations), and why (a global injection check plus tool-specific
     checks must both run; MCP / `ToolProvider` tools can only be guarded from the builder). A code example of a
     global or class-level guardrail that **skips** some tools via `toolName()` / `toolSource()` (there is no
     per-tool opt-out yet). Document the deduplication rule: the same guardrail class from annotations or `*GuardrailClasses(..)` at several levels runs once, instances run once per distinct object, and a builder instance plus an annotation class naming the same class both run
   - security notes: never quote the checked content in a failure message; LLM-as-judge injection; rejected
     output retry loop / cost (use `fatal` or a "do not retry" message); `maxToolCallingRoundTrips`
   - no retry/reprompt (why), and how the LLM self-corrects
   - observability: the new events and listeners
   - unit-testing tool guardrails (mirror the existing "Unit Testing" sections)
   - a short "differences from Quarkus" note (verbatim message with no prefix, runs on error results, chain
     continues after a non-fatal failure, core request types)
2. **`docs/docs/tutorials/tools.md`**: a short section linking to tool guardrails; mention it near "Tool Result
   Attributes", "Specifying Tools Programmatically / Dynamically", "Returning immediately", and "AI services as
   tools" (annotation meanings when a proxy is a tool).
3. **`docs/docs/tutorials/mcp.md`**: how MCP tools are guarded (global guardrails + `ToolSource.mcp(key)` routing, with an example; **set `McpClient` `key(..)`**, because the default is a random UUID),
   `toolWrapper` no longer needed for validation, and the multi-server `ToolSource` of the resources-as-tools tools.
4. **`docs/docs/tutorials/observability.md`**: the new events and listeners; the new `CompensationReason` constants.
5. **Compensation docs** (`tools.md`, `non-blocking.md`, `agents.md`, `observability.md`; find them with
   `grep -rn compensat docs/docs`): document D11 (table from task 09), including cross-agent compensation in
   `agents.md` (which also gets the new `AgentBuilder` tool guardrail setters). Explain prominently that a failure (`failure(..)`, or a rewrite with `isError == true`) means a
   failed step, while a rewrite keeping `isError == false` sanitizes without rollback. **Spell out how far a failure
   reaches:** every compensable tool of the **whole invocation** is rolled back, not only those of the current round,
   and with cross-agent compensation also the compensable tools that **other agents ran before** (@dliubarskyi).
   Point injection guardrails on read tools to `successWith(..)` when rollback isn't wanted. Check that existing
   compensation docs don't repeat the "same tool-calling round" inaccuracy fixed in `CompensationReason`.
6. **Javadoc pass:** every new public type and method has Javadoc with the correct `@since`. Cross-link
   `ToolOutputGuardrail` ↔ `OutputGuardrail` with a sentence explaining the difference. `McpToolProvider.toolWrapper`
   points to guardrails.
7. Check that the docs site builds if there is a local build for it (check `docs/package.json`); otherwise at least
   validate the Markdown links and anchors.

## Files/Areas

- `docs/docs/tutorials/guardrails.md` (+ possibly a new `tool-guardrails.md` and sidebar entry)
- `docs/docs/tutorials/tools.md`, `mcp.md`, `observability.md`, `non-blocking.md`, `agents.md`
- Javadoc across all new and changed public types

## Key Points

- US English throughout.
- Code samples must compile against the final API (copy them from passing tests where possible).
- Use GitHub links to source files in the same style as the existing guardrail docs.

## Done When

- [x] Every PLAN.md "Documentation Must-Haves" item is covered.
- [x] All affected pages are updated and cross-linked.
- [x] Javadoc complete (new public constructors and builder methods documented; the `@since` tags were already in place) with `@since`.
- [x] Docs build: `npm run build` in `docs/` succeeds, no broken link or anchor on the touched pages.

## Extension points table (added 2026-11-16)

`docs/docs/tutorials/guardrails.md` has an "Extension points" section (a table of SPI interfaces, around line 582)
that must be updated by this task:

- `ClassInstanceFactory`: say it also creates tool guardrail classes (annotations and `*GuardrailClasses(..)`), so a
  DI framework needs no tool-specific code for that.
- `ClassMetadataProviderFactory`: it is also used to find `@ToolInputGuardrails` / `@ToolOutputGuardrails`.
- Add rows for the new tool SPIs: `ToolGuardrailServiceBuilderFactory` (task 06b), `ToolInputGuardrailExecutorBuilderFactory` /
  `ToolOutputGuardrailExecutorBuilderFactory` (task 05) and `ToolInputGuardrailsConfigBuilderFactory` /
  `ToolOutputGuardrailsConfigBuilderFactory` (task 04).
- `AbstractGuardrailService` **is** deprecated (decided 2026-11-16, @dliubarskyi was not asked): say that frameworks
  customize guardrails through `ClassMetadataProviderFactory` + `ClassInstanceFactory` with the default builder and
  service, and replace the whole builder through `GuardrailServiceBuilderFactory` only if they must; do not present
  `AbstractGuardrailService` as an extension point.
- **Task 06a additions to document (and to put in the PR description):** `ClassMetadataProviderFactory#resolveMethodKey` (frameworks whose method keys are not `Method`s map the invocation-time `Method` to their keys, so the default builder and service work for them, blocking and non-blocking); the deprecation of `AbstractGuardrailService` and `GuardrailService#aiServiceClass()` (replacement: the SPIs and `targetClass()`), with the migration path for frameworks that subclass it (`langchain4j-cdi` issue [langchain4j/langchain4j-cdi#339](https://github.com/langchain4j/langchain4j-cdi/issues/339); Micronaut keys methods by string and overrides four methods, which `resolveMethodKey` makes unnecessary).
- **Task 06a review additions (2026-11-17), same places (Extension points table and PR description):**
  - `resolveMethodKey` must return the same key for the same method: callers cache the result (the default service
    resolves each key once). Mention it in the `ClassMetadataProviderFactory` row next to `resolveMethodKey`.
  - `GenericGuardrailService` and `GenericGuardrailService.Builder` are **sealed**; `GuardrailService`,
    `GuardrailService.Builder` (and, after 06b, `ToolGuardrailService` / `ToolGuardrailService.Builder`) are
    `non-sealed`. So the extension points are the per-kind interfaces and the SPIs, not the generic types: a framework
    implements `GuardrailService.Builder` (via `GuardrailServiceBuilderFactory`) or `ToolGuardrailService.Builder` (via
    `ToolGuardrailServiceBuilderFactory`), and cannot add a new kind of guardrail service. One sentence is enough; the
    Javadoc of `GenericGuardrailService` has the details.
  - In the `ClassMetadataProviderFactory` row, link `StringKeyedClassMetadataProviderFactory`
    (`integration-tests/integration-tests-class-metadata-provider/integration-tests-class-metadata-provider-string-keys`)
    as the example for frameworks whose method keys are not `Method`s, like the row of `ClassInstanceFactory` links the
    Quarkus and Spring examples. Its tests cover input and output guardrails, method and class annotations, blocking
    and non-blocking (extended 2026-11-17).
  - Do not document `GenericGuardrailService` itself as something users or frameworks extend; it is the shared contract
    (`targetClass()`, execute / has by key) that the per-kind interfaces specialize.

## Outcome

Reviewed by the user, committed as `0baf7c3fd` and pushed to the fork, 2026-11-18. See PLAN.md > Task Outcomes > Task 13 for the file list.

- New page `tool-guardrails.md` instead of a section of `guardrails.md` (the question timed out; the user kept the page). The extension points table is a shared MDX partial shown on both pages.
- Code samples were written against the final API but not compiled.
- Not run: the full Maven test suite (docs and Javadoc only; `install -DskipTests` for core and `langchain4j` passes).
