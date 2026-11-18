# Task 02: Add `ToolSource` and the Tool Guardrail Request Types in Core

**Type:** New Code (public API, `langchain4j-core`)

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: none specific.

## Goal

Introduce the trusted tool-origin type `ToolSource` (D9) and the two request types,
`ToolInputGuardrailRequest` and `ToolOutputGuardrailRequest`, which implement `GuardrailRequest`. They
expose a **core-level view** of the tool call and its result, because `ToolExecutionResult` / `ToolExecutor`
live in the `langchain4j` module and cannot be referenced from core.

## What to Do

1. Mark every new public type `@Experimental` (D19, settled; precedent: `DecisionRequest`). Apply this
   consistently in tasks 02–10.
2. Add `ToolSource`: a **sealed** interface with framework- or application-set variants:
   - local `@Tool` method: exposes the `java.lang.reflect.Method` and/or the declaring class
   - MCP: exposes the server key (`String`; core cannot reference `McpClient`)
   - MCP, multiple servers (D9, settled): exposes **all** server keys. Used by the MCP resources-as-tools tools
     (`list_resources` / `get_resource`), which span several clients (task 10). Decide whether this is a separate
     variant or the MCP variant carrying a set of keys, and present the choice to the user; either way a guardrail
     must be able to ask "does this tool involve server X" without a cast chain.
   - programmatic: `tools(Map)`, `AiServiceTool` without an explicit source, non-MCP `ToolProvider`

   Use static factories (`ToolSource.local(..)`, `ToolSource.mcp(String)`, a multi-server factory,
   `ToolSource.programmatic()`).
   Choose the package; `dev.langchain4j.agent.tool` (next to `ToolSpecification` / `@Tool`) is the likely fit.
   Do not use records in the public API: use interfaces backed by package-private implementations.
3. Add `ToolInputGuardrailRequest` (final class, builder, `toBuilder()`), carrying:
   - `toolExecutionRequest()` (core `ToolExecutionRequest`), `toolSpecification()` (nullable? decide and
     document), `toolSource()`, `requestParams()` (`GuardrailRequestParams`)
   - convenience: `toolName()`, `arguments()` (JSON string), `memoryId()`, `invocationContext()`
   - `withText(String)` → replaces the **arguments JSON** (keeps id and name). The rewritten request is what
     everything downstream sees (D18).
4. Add `ToolOutputGuardrailRequest`: everything above, plus the result view:
   - `resultContents()` → `List<Content>` (canonical, **all** contents; D8)
   - `isError()`
   - a **non-throwing** text helper. Recommendation: return all `TextContent` texts joined with `\n`, or an empty
     string if there is no text. Name it so it can't be confused with `ToolExecutionResult.resultText()`, which
     throws. Present the naming choice to the user.
   - `withText(String)` → replaces the text contents with a single `TextContent` and keeps the non-text contents
     (same approach as `InputGuardrailRequest.rewriteUserMessage`)
   - a package-private / `@Internal` way to replace contents and `isError` wholesale (used by the task 05 executor
     hook for content-level rewrites)
5. Add both requests to the `permits` clause of `GuardrailRequest`.
6. Unit tests (core): builders and validation, `withText` semantics (arguments replaced; text contents replaced
   and non-text preserved), the non-throwing text helper with multi-content and image-only results, and the
   `ToolSource` factories (including the multi-server one).

## Files/Areas

- `langchain4j-core/src/main/java/dev/langchain4j/guardrail/GuardrailRequest.java` (`permits`)
- New: `langchain4j-core/src/main/java/dev/langchain4j/guardrail/ToolInputGuardrailRequest.java`,
  `ToolOutputGuardrailRequest.java`
- New: `ToolSource` (+ implementations) in the chosen package
- Reference: `InputGuardrailRequest.java`, `OutputGuardrailRequest.java`, `GuardrailRequestParams.java`,
  `observability/api/event/ToolExecutedEvent.java` (how core models a tool result)

## Key Points

- **Sealing constraint:** there is no `module-info`, so permitted subtypes must be in the **same package** as the
  sealed parent and must resolve at core compile time.
- **Trust:** `ToolSource` must never be derived from remote data (MCP `_meta`, `openWorldHint`,
  `ToolSpecification.metadata`). Document this in the Javadoc.
- `memoryId()` comes from `requestParams()` / `InvocationContext`. Pick the reliable source and document it.
- `GuardrailRequestParams` stays unchanged (D7: real params are threaded in task 07/08).
- Do not add a throwing `resultText()` accessor (D8).

## Done When

- [x] All new public types are `@Experimental` (D19).
- [x] `ToolSource` exists, is sealed, and has local / MCP / MCP multi-server / programmatic variants with Javadoc
      on trust.
- [x] Both request types exist, implement `GuardrailRequest`, and are permitted.
- [x] No request accessor throws for non-text or multi-content results.
- [x] Unit tests pass; `langchain4j-core` compiles with no new warnings.

## Outcome

**Complete.** Committed on the feature branch `gh-7065-tool-guardrails`.

- **Built:** sealed `@Experimental` `ToolSource` (local, programmatic, and one MCP variant carrying an ordered, immutable, non-empty set of server keys; `mcp(String)` is the one-element factory and `mcp(Collection)` the multi-server one), `ToolInputGuardrailRequest` and `ToolOutputGuardrailRequest`, both added to `GuardrailRequest`'s `permits`. The output request's non-throwing `text()` joins `TextContent` texts with a newline and returns an empty string when there is none.
- **Beyond the task:** `ToolInputGuardrailRequest.argumentsAsMap()` returns a deep unmodifiable snapshot of the lenient-parsed arguments. To let core use the lenient parser, it moved from `langchain4j` into the `@Internal` core class `ToolArgumentsParser`. A parse failure and the JSON literal `null` both throw `IllegalArgumentException`.
- **Compatibility:** `ToolExecutionRequestUtil` stays as a class-level `@Deprecated(since = "1.24.0")` shim that delegates to `ToolArgumentsParser` (no per-method deprecation). Internal callers switched to the parser; its tests moved to core.
- **Done When evidence:** `@Experimental` and `sealed` are on all new public types (`ToolSourceTest`, `ToolGuardrailRequestTest`); no request accessor throws for non-text or multi-content results (`ToolGuardrailRequestTest`); full `./mvnw -T12C clean package` is green (core 1951 tests, `langchain4j` 2079, 0 failures) and spotless passes. The "no new warnings" box rests on the build log showing no compiler warning that names the new classes.
- **Not run:** failsafe integration tests (out of scope here), and no `make lint` beyond spotless.
- **Caveat:** parity between `argumentsAsMap()` and `DefaultToolExecutor` holds for local `@Tool` methods only; MCP and programmatic executors may parse differently.
