# Review of Task 10 (Gemini): `ToolSource.mcp(..)` on MCP tools

Reviewed 2026-11-18 against `task-10-mcp-tool-source.md` and PLAN.md (D5, D9, D10, D16, D20). The work is uncommitted.
The tests were not run during this review, so the "475 unit tests / 70 ITs pass" claim in PLAN.md is unverified.

## What matches the plan

- Every MCP tool is now an `AiServiceTool` stamped `ToolSource.mcp(mcpClient.key())`. The key comes from the
  application-configured client and never from server data.
- The name and specification mappers, `toolWrapper` and the always-visible metadata are unchanged. The source is stamped
  on the original client, so the mappers cannot affect it.
- `list_resources` and `get_resource` get the multi-server `ToolSource.mcp(keys)` built from `consideredMcpClients`.
- The random-UUID default key is left alone. The caveat is documented in Javadoc on `McpToolProvider`, `McpClient#key()`
  and `DefaultMcpClient.Builder#key`.
- The `toolWrapper` Javadoc now points to the tool guardrails.
- No new builder API, no guardrail logic in the MCP module (black-box rule), no D-numbers in source, US English.

## Findings

### 1. Resource tools can reach more clients than their `ToolSource` names (fix before committing)

- The resource executors now receive `consideredMcpClients` instead of `mcpClients`. This is needed so the executor and
  the source agree, and it closes a hole where a subset call stamped a narrow source on a tool that still reached every
  client. It is still a behavior change to `McpToolProvider` that the task file never mentions.
- No test asserts that the executors span only the subset. The new test checks the source only.
- `Collections.unmodifiableList(consideredMcpClients)` is a live view. On the default path `consideredMcpClients` is the
  live `CopyOnWriteArrayList`, so a later `addClient` / `removeClient` changes what the executor reaches. The key set is
  a snapshot. A guardrail could see "alice, bob" while the tool also reaches "carol".
- Suggested fix: use `List.copyOf(consideredMcpClients)` for both the executors and the keys. Add a test that the
  resource executors call only the considered clients. Mention the behavior change in the PR body.

### 2. The catch-clause change swallows and rethrows more than intended (fix before committing)

- `catch (IllegalConfigurationException | IllegalArgumentException e) { throw e; }` was added so that
  `ToolSource.mcp(key)`, which throws `IllegalArgumentException` on a blank or null key, is not swallowed.
- It changes behavior for everything else in the `try`. An `IllegalArgumentException` from `listTools()` or from a
  user-supplied mapper used to be logged and skipped when `failIfOneServerFails=false`. Now it aborts the whole provider.
- A custom `McpClient` with a null or blank `key()` used to work and now fails with the unhelpful "serverKey must not be
  blank".
- Suggested fix: compute the `ToolSource` before the `try`, or validate the key explicitly with a message naming the
  client. Then leave the catch clause as it was. `key()` is also re-called per tool and could be hoisted out of the loop.
- There is no test for the blank-key case either way.

### 3. Empty `consideredMcpClients` falls back to `ToolSource.programmatic()`

- It avoids the `ensureNotEmpty` exception, but it is undocumented and untested.
- A guardrail routed on `ToolSource.Mcp` skips those two tools in that case. The executors reach no servers then, so the
  risk is small, but it should be a deliberate, documented and tested choice.

### 4. Unrelated or noisy changes in the diff

- `.gitignore` gains `/skills/`. This is not part of task 10, and the untracked `skills/` stays out of commits. Drop it
  unless it was added on purpose.
- `McpToolProviderTest` was reformatted wholesale: imports reordered, existing `setUp` and tests re-wrapped, a trailing
  newline added. This makes the real diff harder to review. Consider limiting the change to the new tests and imports.

## Test quality

- The four new unit tests cover the plan's list: multiple clients, mappers, wrapper, subset keys.
- In the resources test, `wrappedCount` is incremented but never asserted. Either assert it (it would prove the wrapper
  applies to both resource tools) or remove it.
- Several separate `assertThat` calls could be chained into one (for example the `serverKeys()` and `involves()` checks).
- The integration tests live in `McpToolsTestBase`, so they run on all 8 transport ITs. The plan said "extend one IT",
  so this is heavier than asked, though it gives wider coverage.
- The IT rewrite and reject tests are about 90% duplicated and could share a helper.
- The ITs cover rewrite and reject, `isError` true and false, the source stamped as `mcp(key)`, and a text rewrite
  keeping `isError` (consistent with D11).
- The ITs only exercise single-client tools. The multi-server source on a resource tool is never exercised through a
  real guardrail, so a guardrail routed on `involves(..)` has no end-to-end proof.

## Still open for later tasks

- `mcp.md` and `tools.md` still need the "set `key(..)` if you route on it" note (task 13).
- Task 12 should re-run revapi on `langchain4j-mcp`. PLAN.md says it passed; not verified here.
- #7106 touches `McpToolExecutor` and `DefaultMcpClient`, so a rebase conflict is possible.

## Verdict

Fix findings 1 and 2 before committing. Findings 3 and 4 are cleanup. The rest is fine.
