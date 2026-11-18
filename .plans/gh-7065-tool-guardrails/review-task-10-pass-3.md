# Review of Task 10, Pass 3 (after Gemini's pass-2 fixes): `ToolSource.mcp(..)` on MCP tools

Reviewed 2026-11-18 against `task-10-mcp-tool-source.md`, PLAN.md (D5, D9, D10, D11, D16, D20) and the first two
reviews (`review-task-10.md`, `review-task-10-pass-2.md`). The work is still uncommitted.

**Not run during this review:** no builds or tests. The test counts and IT results in PLAN.md are Gemini's claims only.

**Verdict:** the production change is correct, small and inside the plan. The MCP module only stamps the source and
has no guardrail logic (black-box rule). Nothing blocks a commit. What is left is mostly test cleanup and a few
decisions for the PR body.

## Verified

- **Each client's tools get its source.** Every tool from a client is an `AiServiceTool` with `ToolSource.mcp(key)`.
  The key comes from the client's `key()`, which the application sets, never from server data. The name and
  specification mappers, `toolWrapper` and the always-visible metadata behave as before.
- **Resource tools.** `list_resources` and `get_resource` get the multi-server source. Their executors and their
  source use the same immutable snapshot (`List.copyOf`), so they cannot drift apart.
- **Pass-2 findings handled:**
  - The original `catch` clause is back.
  - The key check sits before the `try`, throws `IllegalConfigurationException`, and builds its message with
    `formatted()` and the client's class name.
  - `key()` is called once per client.
  - The `toolWrapper` Javadoc now says guardrails run outside the wrapper.
  - The class Javadoc says the resource tools only reach the clients passed to that call.
- **The snapshot test now proves the fix** (`McpToolProviderTest.java:320`). It mutates the provider after
  `provideTools(request)` and checks that the removed `bob` is still called and the added `dave` is not. Without the
  snapshot it would fail.
- **Duplicate keys are safe.** Core's `ToolSource.mcp(Collection)` dedupes through a `LinkedHashSet`, so two clients
  with the same key do not throw.
- **This is the only place MCP tools are built.** No other code in the repo creates a `McpToolExecutor` or implements
  an MCP `ToolProvider`.
- **Javadoc:** the random-UUID default key is documented in all three places (`McpToolProvider`, `McpClient#key()`,
  `DefaultMcpClient.Builder#key`).
- **Rules:** no D-numbers in the source, US spelling throughout, no new builder API on `McpToolProvider`.
- **Branch sync (local refs):** 10 ahead / 0 behind `upstream/main`, and level with the fork branch.

## Findings

### 1. `.plans/` and `.gitignore` (resolved; one leftover)

- `.plans/` is committed on purpose and ships with the PR (OK'd by @dliubarskyi). Not an issue.
- Leftover: the `/.tasks/` line in `.gitignore` no longer matches anything since the plans moved. Remove it or leave
  it, as you prefer.

### 2. Behavior changes for the PR body (recorded in PLAN.md; still need an OK from @jmartisk)

Gemini wrote all four into the task 10 outcome in PLAN.md:

1. Resource tools now reach only the clients passed to that call, not all clients.
2. With an empty client list, `list_resources` and `get_resource` are not emitted.
3. A client with a null or blank key now fails `provideTools` even with `failIfOneServerFails=false`.
4. A `null` entry in the client list now throws a `NullPointerException` instead of being logged and skipped.

Two points to weigh:

- **The key check runs at `provideTools` time, not at build time.** Gemini's reason is that downstream mocks of
  `McpClient` that never call `provideTools` keep working. That is fair, but a misconfigured client now fails at the
  first chat rather than when the provider is built. Decide this on purpose.
- **`key("")` used to work and now fails.** `DefaultMcpClient` only defaults a `null` key to a UUID, so an explicit
  blank key reaches the new check. Mention it alongside item 3.

### 3. Production code style (low)

- `McpToolProvider.java:187` walks two parallel lists by index (`clientsSnapshot.get(i)` and `clientKeys.get(i)`).
  Pairing each client with its key or source in one pass would avoid the index coupling and fit the functional style
  better.
- **Class Javadoc:**
  - `{@link ToolSource#mcp(String) ToolSource.Mcp}` links to the factory method but is labeled as the type.
  - The "clients considered by the call" wording only makes sense for the protected `provideTools(.., List)`
    overloads; public callers always get every client.
  - One long sentence with a parenthesis is hard to read. Split it.
- **`toolWrapper` Javadoc:** "they run automatically outside this wrapper for MCP tools" suggests this is special to
  MCP. It is true for every tool.

### 4. Unit tests (cleanup)

- **`McpToolProviderTest.java:244-253`:**
  - Casts to `ToolSource.Mcp` without a check.
  - Nests an `assertThat` inside `satisfies(keys -> …)` and never uses `keys`.
  - Re-tests core's `involves(..)`, which the exact tuple check above already covers. Test our own contracts: drop
    that block, or keep only `serverKeys().containsExactly(..)`, which checks the key order.
- **`McpToolProviderTest.java:205-207`:**
  `assertThat(tool1).extracting(..., tool -> wrapperCalls.get(), tool -> executionResult.resultText())` bends
  `extracting` around values that are not on `tool1`, just to have one `assertThat`. Separate assertions are clearer
  here.
- **These tests still do more than one job:**
  - `should_scope_resource_executors_to_considered_clients_and_wrap_them` checks scoping, the wrapper and the
    unknown-server error, with 7 separate assertions.
  - `should_preserve_mcp_tool_source_with_name_and_specification_mappers` covers two scenarios.
  - `should_reject_null_or_blank_client_key` covers two cases; a `@ParameterizedTest` with `@NullSource` /
    `@ValueSource` would suit it.
- **No test for item 4 of finding 2** (a `null` client entry).
- **String concatenation:** `+` builds strings in the guardrails (`McpToolProviderTest.java:438` and both IT
  guardrails); prefer `formatted()`.

### 5. Integration tests

- **Coverage is good:** rewrite and reject on both a normal and an `isError: true` result, the guardrail sees
  `ToolSource.mcp(key)`, a rejection gives `isError=true`, and a text rewrite keeps `isError` (D11). One shared
  helper removes the earlier duplication.
- **Not fully run:** the tests live in `McpToolsTestBase`, so they run on all 8 transports. PLAN.md says only the 2
  stdio ITs were run. Streamable HTTP (Apache, OkHttp, legacy) and WebSocket (plus legacy) are unverified.
- **Naming nit:** the new tests use `should_…` snake_case, while every other test in `McpToolsTestBase` is camelCase
  (`executeToolThatReturnsError`).

### 6. PLAN.md and the task file (low)

- The task 10 outcome is one long paragraph, while the other outcomes use bullets.
- The test count changed across passes (475, then 477, then 481), and none of those runs is verified.
- Task 10 is ticked `[x]` in the PLAN.md checklist (line 86) and in the task's "Done When", although the transport
  ITs have not all run and the user has not reviewed it yet.

## Before committing

1. Decide whether the key check should move to build time (finding 2).
2. Run the full `langchain4j-mcp` build and at least one streamable HTTP and one WebSocket IT.
3. Optional cleanup: findings 3 to 6, and the stale `/.tasks/` line in `.gitignore`.
