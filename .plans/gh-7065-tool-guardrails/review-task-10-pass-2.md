# Review of Task 10, Pass 2 (Gemini's follow-up): `ToolSource.mcp(..)` on MCP tools

Reviewed 2026-11-18 against `task-10-mcp-tool-source.md`, PLAN.md (D5, D9, D10, D11, D16, D20) and the first review
(`review-task-10.md`). The work is still uncommitted.

**Verdict:** the production change is correct and keeps the guardrails a black box: the MCP module only stamps the
source and has no guardrail logic. One test does not prove what it claims (finding A), and four behavior changes need
to go in the PR body (finding B). The rest is cleanup.

## Verified

- **Unit tests:** `McpToolProviderTest` runs 8 tests, all passing.
- **Formatting:** `spotless:check` on `langchain4j-mcp` is clean.
- **Not re-run:** the full 477-test suite, the ITs, revapi and javadoc. Those results are Gemini's claims only.
- **Equivalence:** `AiServiceTool.builder()` defaults to `TO_LLM` and `programmatic()`, so moving off
  `builder.add(spec, executor)` changes nothing except the source.
- **Other modules:** `langchain4j-agentic-mcp` mocks `McpClient` without stubbing `key()`, but it never uses
  `McpToolProvider`, so the new key check does not break it.

## How the first review was handled

- **Finding 1 (resource executors):** fixed. Executors and source now share one `List.copyOf` snapshot. The test meant
  to prove it is vacuous, though (finding A).
- **Finding 2 (catch clause):** fixed. The catch clause is back to the original, and the key is checked before the
  `try`.
- **Finding 3 (no clients considered):** changed to emit no resource tools instead of `programmatic()`. That is the
  better choice, but it is a behavior change (B2).
- **Finding 4 (unrelated changes):**
  - The `.gitignore` `/skills/` line was added on purpose by the user. Keep it.
  - The full reformat of `McpToolProviderTest` is not optional. Spotless uses `ratchetFrom origin/main`, so a touched
    file is reformatted in full. Drop this half of the finding.

## Findings

### A. The snapshot test passes even without the snapshot (medium; fix before committing)

- The test adds `dave` with `addMcpClient(..)` and then runs the resource tools from `subsetResult`.
- That subset came from an immutable `List.of(alice, charlie)`, so `dave` can never be reached, with or without
  `List.copyOf`. Removing `List.copyOf` keeps the test green.
- The live-list risk only exists on the default path, where `provideTools(request)` passes the live
  `CopyOnWriteArrayList`.
- Fix: call `addMcpClient(dave)` after `allResult` and run `allResult`'s `list_resources`, asserting `dave` is never
  called.

### B. Behavior changes the task file does not mention (put in the PR body; probably needs an OK from @jmartisk)

1. **Resource tools are now limited to the clients passed in.**
   - Before, they reached all clients through a live view, even when a subset was passed.
   - This matches the stated goal of [#5602](https://github.com/langchain4j/langchain4j/pull/5602) (requests for a subset of clients should "not call any methods on clients
     that should be ignored"), which is a good argument for it.
   - It changes what Quarkus `@McpToolBox` users get.
2. **No clients passed in means no `list_resources` / `get_resource` tools.**
   - Before, both tools were emitted and spanned every client.
   - Only a parenthesis in the class Javadoc says so, and the only test is one `isEmpty()` at the end of the big test.
3. **A null or blank `key()` now throws on every `provideTools` call, even with `failIfOneServerFails=false`.**
   - Before, tools from such a client worked.
   - It breaks custom `McpClient` implementations and Mockito mocks that do not stub `key()`, possibly in downstream
     (Quarkus) tests.
   - It fails late: at the first chat, not at build time. Decide whether it should fail at build time instead (see C).
4. **Nit: a `null` entry in the client list fails differently.** `List.copyOf` now throws an NPE outside the `try`.
   Before, the NPE was caught and logged when `failIfOneServerFails=false`.

### C. The exception and its message in `requireClientKey` (low)

- It throws `IllegalArgumentException`. This same method already treats `IllegalConfigurationException` (already
  imported) as "configuration error, always propagate". That fits better and is closer to the domain-exception rule.
- The message embeds `mcpClient.toString()`. `DefaultMcpClient` does not override it, so users would see
  `DefaultMcpClient@6d06d69c`.
- The message is built with `+`; prefer `formatted()`.
- `key()` is checked twice per client (once in the loop, once in the resource stream). Collecting the keys in the loop
  avoids the second pass.
- Optionally, also check the key in the constructor and in `addMcpClient`, so the error shows up at build time.

### D. Gaps in the Javadoc (low)

- **`toolWrapper`:** say that guardrails run outside the wrapper. The wrapper receives input already rewritten by input
  guardrails, and output guardrails see the wrapper's output. That matters for the tracing wrappers that are the reason
  to keep it (D16).
- **Class Javadoc:** say that the resource tools only reach the clients passed to that call.

## Test quality

- **One test does seven jobs.** `should_stamp_multi_server_tool_source_and_scope_executors_to_considered_clients_snapshot`
  checks:
  - the source with all clients;
  - the source for a subset;
  - which clients the executors reach;
  - snapshot isolation;
  - the wrapper on the resource tools;
  - an unknown server;
  - an empty client list.

  Split it. It also picks tools by position (`get(2)` / `get(3)`) and uses weak substring checks (`contains("alice")`).
- **`should_reject_null_or_blank_client_key_…` only tests a blank key, not null,** despite its name. An unstubbed mock
  already returns null for `key()`, so adding that case is trivial. The test also covers two unrelated behaviors
  (blank-key rejection and `failIfOneServerFails=false` on a throwing `listTools()`); split it.
- **Assertions are not chained:**
  - the `satisfies` block holds four separate `assertThat`s;
  - the wrapper test uses three separate `assertThat`s;
  - `allSatisfy(isInstanceOf(ToolSource.Mcp.class))` is redundant after the exact tuple check.

  `var res` is a naming nit.
- **Good:** a new full AI-service test routes a guardrail on `involves(..)` for a resource tool, which closes the gap
  from the first review. The ITs now share one helper and cover rewrite and reject for both `isError` false and true.
  Rejection gives `isError=true`, and a text rewrite keeps `isError`, both as D11 requires.
- **The ITs are only partly verified.** They live in `McpToolsTestBase`, so they run on 8 transports, but PLAN.md says
  only the 2 stdio ITs were run. Streamable HTTP (plus Apache, OkHttp, legacy) and WebSocket (plus legacy) are
  unverified.
- **Minor:** the IT checks the source against a random UUID key, which proves the key reaches the guardrail but not
  that an explicitly configured key does. There is no MCP-specific async or streaming test; task 08 already covers
  those code paths, so task 11 is the place if one is wanted.

## Clean

- No decision IDs in the source, US spelling throughout.
- No new builder API on `McpToolProvider` (D10).
- The default UUID key is unchanged; the caveat is documented on all three Javadoc sites, and `mcp.md` gets it in
  task 13 as planned.

## Before committing

1. Fix A.
2. Write up B in PLAN.md (Task Outcomes / PR body notes for task 14), and decide whether B3 should fail at build time.
3. C, D and the test-quality items are cleanup.
