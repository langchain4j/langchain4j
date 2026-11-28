# Task 10: Stamp `ToolSource.mcp(..)` on MCP Tools and Prove MCP Tools Are Guarded

**Type:** Code Modification (`langchain4j-mcp`)

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: [#7106](https://github.com/langchain4j/langchain4j/pull/7106) (touches `McpToolExecutor` and `DefaultMcpClient`).

## Goal

Make every MCP tool carry a trusted `ToolSource.mcp(serverKey)` (D9), so a global or per-tool guardrail can
tell MCP tools apart from local ones and one MCP server from another. Prove end to end that MCP tools are
guarded exactly like local tools, with no `toolWrapper` needed.

**Out of scope (D10):** guardrail configuration on `McpToolProvider.builder()` (a provider-wide list or a
per-client function). That is a follow-up; ask @jmartisk whether it should absorb [#5345](https://github.com/langchain4j/langchain4j/pull/5345).

## What to Do

1. In `provideTools(..)` (~lines 150-230), emit `AiServiceTool`s (instead of bare `(spec, executor)` pairs) with
   `toolSource(ToolSource.mcp(mcpClient.key()))`. Keep the mapped `ToolSpecification`, the `toolWrapper`, and the
   always-visible/search metadata behavior unchanged.
2. **Resources as tools** (`createListResourcesExecutor` / `createGetResourceExecutor`, ~lines 216 / 223): these span
   **multiple** clients, so there is no single key. **Settled (D9):** stamp the multi-server `ToolSource` variant from
   task 02, carrying the keys of all clients the tool spans (the `consideredMcpClients` of that call, after
   filtering). Resource content is a prime injection vector, so the source must be as specific as possible.
3. **Default key caveat:** `DefaultMcpClient` defaults `key` to a random UUID (`DefaultMcpClient.java:152`). Do not
   change that default. Document in the Javadoc and in `mcp.md` (task 13) that users must set `key(..)` if they want
   to route guardrails on the server key.
4. Keep `toolWrapper` (D16) working: it wraps the executor, and guardrails run in `ToolService` outside it. Update
   its Javadoc to point to tool guardrails for validation use cases.
5. Tests:
   - `McpToolProviderTest`: every emitted tool carries `ToolSource.mcp(key)` for the right client (multiple clients);
     name/spec mappers don't affect the source; `toolWrapper` is still applied; the resources-as-tools tools carry the multi-server source with exactly the
     considered clients' keys
   - extend one existing `McpTools*IT`: a global `ToolOutputGuardrail` sees `toolSource()` as `mcp(key)` and can
     rewrite and reject a real MCP tool result, including an MCP `isError: true` result (D5)

## Files/Areas

- `langchain4j-mcp/src/main/java/dev/langchain4j/mcp/McpToolProvider.java`
- `langchain4j-mcp/src/test/java/dev/langchain4j/mcp/McpToolProviderTest.java`
- `langchain4j-mcp/src/test/java/dev/langchain4j/mcp/client/integration/McpTools*IT.java`

## Key Points

- `McpClient.key()` is configured by the application and is therefore trustworthy. Never use server-provided
  names, `_meta`, or hints such as `openWorldHint` as the source.
- No new public builder API on `McpToolProvider` in this PR.

## Done When

- [x] Every emitted MCP tool carries `ToolSource.mcp(key)`.
- [x] The resources-as-tools tools carry the multi-server `ToolSource` with the right keys.
- [x] The default-UUID-key caveat is documented in Javadoc (docs in task 13).
- [x] Unit tests and at least one MCP IT (including an `isError` result) pass.
