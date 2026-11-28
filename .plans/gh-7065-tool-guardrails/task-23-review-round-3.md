# Task 23: Review Round 3

**Type:** Bugs first (uncommitted until the user says so), then discussion of the API questions

> **Why:** @dliubarskyi reviewed round 2 on 2026-11-27
> ([comment 7216319925](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7216319925)). He approved
> round 2 and the change to the deprecated `executeTool(..)` / `startTool(..)`, ran the integration test modules
> locally on `1c218ba4d` (green), and said the red `integration_test` comes from `main` (the Tika revapi break, fixed by
> #7324): the next merge of `main` clears it.
>
> **How (user, 2026-11-27):** fix the bugs first and stop before committing; discuss the questions after.

## Bugs and inaccuracies (fixed, UNCOMMITTED, awaiting review)

1. **Malformed arguments failed the call when the tool had any guardrail.** `ToolGuardrailService.changedArguments`
   parsed the executed arguments outside the `try` and caught only `ToolArgumentsException`, while the parser throws the
   codec's exception (a `JsonEOFException` for `{"q":`). Fix: a call that no input guardrail rewrote is not parsed at
   all; the arguments of the LLM are parsed in `modelArguments(..)`, which catches any `RuntimeException` (all the
   arguments the tool ran with are then the changed ones). Tests: blocking and the 4 non-blocking modes without an
   input guardrail (only a pass-through output guardrail), and a note for unparseable LLM arguments that an input
   guardrail repaired. All 6 fail on the old code with the `JsonEOFException`.
2. **The `failure(..)` message of an output guardrail gets the note after an input rewrite** (kept, as Dmytro agrees):
   Javadoc of `ToolOutputGuardrail.failure(..)` and `ToolOutputGuardrailResult.Failure`, `tool-guardrails.md` (the note
   section, the failures section, the Quarkus differences); a test pins it. The PR description's "verbatim" needs the
   same sentence (draft ready, not posted).
3. **Fully qualified names in new Javadoc:** `ToolInputGuardrail`, `GuardedToolExecution` (`ToolInputGuardrailsConfig`)
   and `AiServiceTool` x3 (`Method`) now use imports. The only other one in the PR's added lines is
   `ToolGuardrailContextTests` (`dev.langchain4j.data.message.UserMessage`), which must stay qualified next to the
   `@UserMessage` annotation (the upstream guardrail tests do the same).
4. **`DefaultToolInputGuardrailsConfig`:** the builder field is a `@Nullable Boolean`, defaulted with
   `getOrDefault(.., true)` in the constructor, like the template.
5. **`ToolRoundCall`:** `toolResult`, `guardrailFailure` and `execution` are JSpecify `@Nullable`.
6. **`sameMethodIn` matched an overload in a superclass** (`search(Object)` for a `search(String)` tool) through its
   "assignable either way" fallback. A generic override now needs the bridge method the compiler adds to the subtype
   (`overridesThroughABridge`); more than one candidate matches none. The "whichever of them carries `@Tool`" wording
   (resolver, both annotations, `tool-guardrails.md`) is gone: `@Tool` can be on any method of the chain, and an
   overload is not part of it. Tests: the overload case (fails on the old code) and a generic interface method that
   carries the guardrail.
7. **"The result the LLM saw"** in `ToolCompensatedEvent`, the stream `ToolCompensatedEvent`, `ToolService`,
   `tool-guardrails.md` and `observability.md`: the events carry the guarded result without the note.

Verification (offline, 2026-11-27): core 2205, `langchain4j` 2605, agentic 690, MCP 510 (unit), guardrails 111,
guardrail ITs 366, revapi (core, `langchain4j`, MCP, agentic): all green.

## Dmytro's Commits (2026-11-27)

Dmytro then pushed four commits on top of `1c218ba4d`
([comment 7220639168](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7220639168)): `43007ddc1`
(code fixes), `e6de71a6c` (stricter, deterministic tests), `2d6666e90` (docs) and `0cbe85aab`
(`ToolGuardrailService.toolInputGuardrailsConfig()` is abstract). He also posted 13 design questions
([comment 7220640809](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7220640809)). We replied
that we would answer after the Thanksgiving holiday
([comment 7221980681](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7221980681)).

Reconciliation (UNCOMMITTED, on top of his commits and a merge of `upstream/main`, `18f9ee106`, pushed):

- Three trivial conflicts: the imports of `AiServiceTool` and `ToolArgumentsRewriteNoteTest`, and the fixtures of
  `ToolGuardrailResolutionTest` (both sets kept).
- Our test of the note in front of an output failure is dropped: his
  `the_note_also_precedes_the_message_of_an_output_guardrail_that_rejects_the_result` covers it and also checks
  `ToolExecution`.
- Item 6 works with his resolver changes (JDK proxies use the declaring class, interfaces level by level):
  `sameMethodIn` runs for each class of the new `hierarchy(..)`.
- Item 2, the other places he listed: the rollback note of `tool-guardrails.md` and `tools.md`, and the two rollback
  Javadocs of `ToolService` (the error result is kept as it is, with the note after an input rewrite). The places in
  `GuardedToolExecution` and `ToolGuardrailService` stay: the result there has no note yet (callers apply
  `resultTextForLlm`). The security note ("the message is sent to the LLM verbatim") also stays.
- Item 7: nothing is left after the merge.
- Verification (offline, after the reconciliation): core 2205, `langchain4j` 2636, agentic 693, MCP 547 (unit),
  guardrails 111, guardrail ITs 370, revapi (core, `langchain4j`, agentic, MCP): all green.
- Item 1: the test with an input rewrite of `{"q":` exists. What it expects (all the arguments the tool ran with)
  is his design question 3, so it changes with that decision.

## Questions (to discuss, nothing done)

- `ToolSource`: plain top-level interface instead of sealed (`Local`, `Mcp`, `Programmatic` stay sealed), docs say to
  expect other kinds.
- Unseal the sealed guardrail types (`GuardrailRequest`, `GuardrailResult` and `Failure`, `GuardrailException`,
  `GuardrailExecutedListener`, `GuardrailExecutorBuilderFactory`) in this PR; the `@Internal` executors stay sealed.
- The rewrite note template as a `PromptTemplate` (getter, setter, default), dropping the two placeholder constants.
- `GuardedToolExecution` Javadoc: "a tool execution through its tool guardrails: the call as executed and its guarded
  result".
- Rename `ToolRound` to `ToolExecutionRound`.
