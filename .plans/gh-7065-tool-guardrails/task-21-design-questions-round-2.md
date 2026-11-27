# Task 21: Answer the Round 2 Design Questions

**Type:** Discussion first, then code (no code until the user says so)

> **Why:** @dliubarskyi posted 16 open design questions on PR #7202
> ([comment](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7130934995), 2026-11-24) and asked us
> to pick an option for each, or suggest another one.
>
> **How (user, 2026-11-24):** go through them one at a time: discuss the question, evaluate the proposal and the
> alternatives, record the decision here. No code yet.

## Status (2026-11-25)

Dmytro answered on 2026-11-25 (see [Dmytro's Answer](#dmytros-answer-2026-11-25)): everything approved as
written, two docs fixes, and one addition to item 2 (a note in the tool result after an input rewrite). The user
agreed to our position on the addition. Reply posted 2026-11-25 ([comment 7178126059](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7178126059)): accepts the docs fixes and the note (this PR), asks him about a result without text, arguments a guardrail added, and the tag / placeholder names. Next: the code (the note details can follow his answer).

### Earlier status (2026-11-24)

All 16 items decided (see the notes). Reply posted 2026-11-24
([comment 7138967540](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7138967540)): asks Dmytro
about item 5 (`TokenStream` in a follow-up PR?) and item 8 (keep `.plans/` in `main`, or remove before merge?),
flags items 1, 15 and 16 as not exactly his options, and asks him to confirm the rest. Next: wait for his answer, then
the code (no code before his OK).

**Process (user, 2026-11-24): from now on, each item is evaluated in its own context.** Take the first open item
only (all done as of item 16), discuss it the same way (question, proposal, alternatives, recommendation), record the decision
here, then stop. Read the earlier notes first: they can affect later items (item 2 -> item 15; items 2 and 3 ->
item 9). No code until the user says so.

## Decisions

| # | Topic | Decision | Notes |
|---|---|---|---|
| 1 | Annotations across the class hierarchy and overrides | **Refined B: levels add up, nearest wins within a level** (user 2026-11-24, replaces an earlier A) | See notes, item 1 |
| 2 | Chat memory after an input rewrite | **B: memory keeps the LLM's original call** (user 2026-11-24) | See notes, item 2 |
| 3 | Non-fatal failure: keep running the chain or stop | **A: keep running** + docs note; stop-on-first-failure stays an opt-in follow-up (user 2026-11-24) | See notes, item 3 |
| 4 | `IMMEDIATE` + output rewrite returns `null` | **B: `String` gets the guarded text, other types fail fast** (user 2026-11-24) | See notes, item 4 |
| 5 | Cancellation of a call still queued on the executor | **A, one rule for all non-blocking modes**: `CompletableFuture` / `Flow.Publisher` in this PR, `TokenStream` in a follow-up PR (proposal; no issue yet) (user 2026-11-24) | See notes, item 5 |
| 6 | Tools without a binding run unguarded | **A: bind on demand (fail closed), `Map<String, ToolKey>` replaces `ToolGuardrailBinding`** (user 2026-11-24) | See notes, item 6 |
| 7 | Spring proxied `@Tool` beans get `Programmatic` | **A, inferred in the `AiServiceTool` default** (user 2026-11-24); adds a JDK-proxy rule to item 1 | See notes, item 7 |
| 8 | `.plans/` in the PR | **B: keep it (merged into `main`)**, scrub local paths, trim session-state notes; fall back to D if Dmytro objects (user 2026-11-24) | See notes, item 8 |
| 9 | `tool*GuardrailsConfig(..)` setters that configure nothing | **B: keep them, parity with the AI service guardrails** (user 2026-11-24; already decided in task 6b) | See notes, item 9 |
| 10 | Compensation events carry the raw result | **A: both compensation events carry the guarded result, the compensating action keeps the raw one**, + a warning that the tool guardrail events carry unredacted data (user 2026-11-24) | See notes, item 10 |
| 11 | `afterToolExecution` throws: rollback and event parity | **A: keep the rollback, every mode reports the error** (user 2026-11-24) | See notes, item 11 |
| 12 | `toolSpecification()` nullable on requests | **A: required**, + the `execute*Guardrails(key, request)` entry points fail fast when the request disagrees with the key (user 2026-11-24) | See notes, item 12 |
| 13 | `ToolArgumentsException` from an input guardrail skips output guardrails | **B: keep it, documented as one rule: output guardrails check what the tool produced; results made before the tool runs skip them** (user 2026-11-24) | See notes, item 13 |
| 14 | Missing `validateAsync` discovered after the tool ran | **B: keep discovering it by calling it, documented** (user 2026-11-24; rejected a reflection pre-check) | See notes, item 14 |
| 15 | Thinking ITs on old models | **D: delete both ITs** (item 2 removes the edited signed turn they test) (user 2026-11-24) | See notes, item 15 |
| 16 | `successWith(Map)` for input rewrites | **C: a separately named `successWithArguments(Map<String, ?>)`, serialized eagerly with the JSON codec, `null` rejected** (user 2026-11-24) | See notes, item 16 |

## Discussion Notes

### Item 1: refined B, user decided 2026-11-24 (replaces A, agreed earlier the same day)

- **Rule: the levels add up, and within a level the nearest declaration wins.** The D6 levels (builder, class,
  method, `AiServiceTool`) still add up.
- **Class level:** the nearest annotation in the hierarchy wins: the class, then its superclasses, then its
  interfaces (Spring's search order; today's `findClassAnnotation`).
- **Method level:** the nearest annotation in the override chain wins: the concrete method of the runtime class, then
  the overridden superclass methods, then the interface methods, whichever of them carries `@Tool`. Fixes `@Tool` on
  an interface + guardrail on the implementation (Spring / CDI), and the reverse. Skip bridge methods; test generic
  interfaces (`getDeclaredMethod` with exact parameter types can miss an override).
- `tools(List<AiServiceTool>)` / `tools(Map)`: use the runtime class of the `DefaultToolExecutor`'s target object
  instead of the method's declaring class, through an internal accessor (no new public getter).
- **JDK proxies (added with item 7, user 2026-11-24):** use the runtime class only when it is a subtype of
  `originalMethod()`'s declaring class; otherwise (a JDK dynamic proxy, `$ProxyNN`, which only implements the
  interfaces) use the declaring class, or the target class's annotations are lost. CGLIB proxies (Spring Boot's
  default) subclass the target, so the hierarchy search reaches it. Test with a JDK proxy.
- A subclass can replace its parent's guardrails, and `@ToolInputGuardrails({})` removes them explicitly.
- DEBUG log when a nearer annotation hides one further up the hierarchy.
- Class annotations still go through `ClassMetadataProviderFactory` (per class of the hierarchy); method annotations
  still by reflection.
- **Why not A:** framework users expect nearest-wins (Spring `@Transactional` / `MergedAnnotations`, Jakarta
  `@RolesAllowed`, JAX-RS annotation inheritance, MicroProfile Fault Tolerance, Java `@Inherited`); Bean Validation is
  the outlier. A and B only differ when parent and child both declare the annotation; proxies and interface /
  implementation splits are handled the same by both.
- quarkus-langchain4j's own resolution does not matter (user): it goes away and is replaced by this one, so this
  becomes the rule Quarkus users get.
- Rejected: A (merge; surprises framework users), C (docs would admit a no-op), fail fast on parent + child
  annotation (punishes legitimate layering).
- Docs: `tool-guardrails.md` "Resolution order": "every tool guardrail that applies runs" becomes "every level
  contributes; within the class level and the method level, the nearest declaration applies"; add an interface +
  implementation example and the subclass-replaces example.

### Item 2: B, user agreed 2026-11-24

- Memory (and the `messages` list without a memory) keeps the LLM's original `AiMessage`; a rewrite only changes
  what the tool runs with and what the events report. Framing for docs: memory is the conversation as the model said
  it, events are what was executed.
- Removes the failing-memory-write-skips-events-and-rollback bug and the signed (thinking) turn risk for every
  provider, not only the ones checked. `ToolRound` stops touching `ChatMemory`: `complete` / `completeAsync` drop
  their memory / messages parameters and `updateHistory` / `updateHistoryAsync` / `replace` go (D20 fit; simpler 4
  loops). Matches Quarkus (#3162).
- Note for the reply: `main` already uses `ChatMemory.set` / `setAsync` for compensation; B removes only this caller.
- Redaction at rest is out of scope for an input guardrail (it protects the tool); docs point to a custom
  `ChatMemory` / `ChatMemoryStore` or an AI service output guardrail.
- A later opt-in "persist the rewrite" stays possible without breaking anything (a setting on
  `ToolInputGuardrailsConfig`; relevant to item 9).
- Rejected: A (moves the late failure instead of removing it, still edits signed turns, id matching unreliable when
  providers return no ids), C (keeps a bug that skips rollback).
- Follow-ups: revert the NOTE on `ChatMemory.set` / `setAsync` to compensation only; update the `ToolRound` Javadoc
  and the rewrite section of `tool-guardrails.md`; replace the memory-rewrite tests with one per mode asserting the
  memory holds the original call.
- Knock-on: item 15's thinking ITs lose most of their purpose (they prove an edited signed turn is accepted).

### Item 3: A, user agreed 2026-11-24

- Keep today's behavior: a non-fatal failure keeps running the chain and the messages are aggregated, like AI
  service guardrails. The user controls the order of their guardrails (user's flexibility argument).
- Facts that shaped the discussion: no failure carries a rewrite, in either family (`failure(..)` results are only
  `(List<Failure>, boolean fatal)`; AI service `OutputGuardrail` `retry` / `reprompt` / `*WithMessageRemoval` do not
  rewrite either); redaction is `successWith(..)`, which continues the chain in every option. For tool guardrails
  `fatal` is not a "stop the chain" switch: it aborts the invocation and rolls back, unlike AI service guardrails
  where failure and fatal both end in an exception.
- Docs: a note under the failure rows of `tool-guardrails.md` that later guardrails still get content an earlier one
  rejected, so the order matters when a later guardrail sends it to an external service or another LLM.
- Keep the PR follow-up "stop on the first failure, as an opt-in setting" (home: `ToolInputGuardrailsConfig` /
  `ToolOutputGuardrailsConfig`; relevant to item 9).
- Not doing: failure-with-rewrite (new API for both families, and an open question of what the LLM sees).
- Rejected: B (two rules), C (removes the user's choice; `fatal` is not the escape hatch for tools).

### Item 4: B with details, user agreed 2026-11-24

- Context: `DefaultAiServices` (~line 1386, same as `main`) returns `ToolExecution#resultObject()` for an all-immediate
  round with a non-`Result` return type. `main` already returns a silent `null` for immediate programmatic / MCP tools
  (`ToolExecutor.executeWithContext` sets only `resultText`); new is that adding a guardrail to a working tool flips it.
  Keeping the raw object is not an option (bypasses the redaction where the value reaches the application).
- A `String` method gets `ToolExecution#result()` (the guarded text). A rewrite with no text (non-text contents only)
  fails fast like the other types.
- Any other non-`Result`, non-`void` return type fails fast with a clear exception: the tool's result was rewritten
  by an output guardrail, so there is no tool object; use `Result<...>` (same advice as `main`'s existing message).
- `void` / `Void` and `Result<T>` unchanged (`resultObject()` stays `null` after a rewrite; guarded text in `result()`).
- Fails at return time, after the tool ran (like `main`'s type-mismatch exception); no build-time warning (whether a
  guardrail rewrites is only known at run time).
- `DefaultAiServices` needs an internal "rewritten" flag on the execution so a dropped object is not mistaken for a
  tool that returned `null` (no new public API).
- Docs: rewrite the "What the AI Service returns" bullet in `tool-guardrails.md`. Tests: immediate `String` returns the
  guarded text; POJO fails with the new message; `Result<String>` unchanged.
- Rejected: A (silent `null` after adding a guardrail), C (breaks the common `String` case for no safety gain).

### Item 5: A, extended to `TokenStream`, split across two PRs; user agreed 2026-11-24

- **Rule:** once the invocation is canceled, a call that has not started does not start. It has no lifecycle:
  neither `beforeToolExecution` nor `afterToolExecution` fires, no `ToolExecutedEvent`, no `ToolCompensatedEvent`
  (nothing ran, nothing to roll back). Calls that have begun run to the end and are rolled back, as on `main`; a
  running tool is never interrupted. Sync mode has no cancel API, so it is not affected.
- Context: on `main`, `ToolService.startTool` fires `beforeToolExecution` inside the executor task with no cancel
  check, so a queued call runs after a cancel and is rolled back only if compensable (a non-compensable side effect
  stays). Moving the check before `beforeToolExecution` keeps the callback on the same (executor) thread.
- **This PR (change 1): `CompletableFuture` and `Flow.Publisher`.** Both go through `ToolRound.startTool` ->
  `ToolService.startTool(round, ..)` -> `executeToolAsync` -> `internalExecuteToolAsync`; today the order is
  dequeue -> `beforeToolExecution` -> `round.executeAsync` cancel check -> `afterToolExecution` with the error. Fix:
  check the round's cancellation before `beforeToolExecution` and skip both callbacks (one place, both modes).
  - Breaking Changes entry: a call that has not started when the invocation is canceled no longer runs and gets no
    callbacks or events; on `main` it ran (and was rolled back only if compensable). Update the Compatibility bullet.
  - `tools.md` (cancellation part, ~line 2158): the rule, plus that `TokenStream` still runs such a call (rolled back
    if compensable) until the follow-up lands.
  - Deterministic tests per mode, no guardrails: single-thread executor blocked on a latch, cancel, release; assert
    the tool never ran, no callbacks, no execution / compensation events. Plus a test that a call that already began
    still runs and is rolled back.
- **Follow-up PR (change 2, proposal only; user: do not open an issue yet): `TokenStream` parity.**
  `AiServiceStreamingResponseHandler` has its own cancellation (`cancelled` flag, the claimed-response race, rollback
  on the canceling thread, from #7168), calls `startRound(..)` without a cancellation future and runs calls through
  `ToolRound.executeTool(.., executor)`, which never checks. Wire the handler's cancellation into the round so a
  queued call does not start. Why separate: not a regression from this PR (same as `main`), no guardrails involved,
  touches #7168's delicate streaming race. Keep it a small standalone PR, not folded into #7218 (bigger, blocked on
  this PR). Same deterministic test for `TokenStream`.
- Rejected: B (starts side effects after a cancel; two rules by guardrail presence), C (a flag for cancellation
  timing).

### Item 6: A with details, user agreed 2026-11-24

- Context: `ToolRound` looks up `binding(call.name())`; a tool without an entry runs with no guardrails, the global
  ones included. Gaps: the built-in tool search tool (documented, `tool-guardrails.md` lines 49 and 294) and every
  tool of a framework-built `ToolServiceContext`. `ToolGuardrailBinding` is `@Internal` with a package-private
  constructor and `bind(..)`, while `ToolServiceContext.Builder.toolGuardrailBindings(..)` is public.
- **Fail closed:** a call without a key is bound on demand by the context's service: spec from the context, source
  from the executor (`local(method)` for a `DefaultToolExecutor`, see item 7; otherwise `Programmatic`), class by
  item 1's runtime-class rule. Cheap for unannotated tools (the resolver shares the global executor). A key bound on
  demand is kept for the rest of the round.
- **No service on the context = no tool guardrails**, as now (the deprecated `ToolServiceContext(List, Map)`
  constructor stays without one).
- **API:** remove `ToolGuardrailBinding`; the context holds `Map<String, ToolKey>` (builder setter renamed, e.g.
  `toolGuardrailKeys(..)`), filled by frameworks through the public `ToolGuardrailService.bind(..)`. A foreign key
  fails fast (task 17 owner check). All new in 1.25.0, nothing released breaks. Matters for Quarkus, which will use
  this API instead of its own.
- **Tool search tool:** guarded by the global guardrails. Framing: its output carries tool descriptions, a known
  injection vector ("tool poisoning"). Docs: drop the two "not guarded" exceptions, add that sentence and how to skip
  it by name.
- **Guardrail params:** the round falls back to the invocation's common guardrail params (`AiServiceTokenStream`'s
  `commonGuardrailParams`) before the empty fallback in `ToolRound.guardrailRequestParams()` (~line 777).
- Tests: framework-built context with keys from `bind(..)` runs guardrails; context with a service but no keys runs
  the global guardrails; tool search output goes through a global output guardrail; foreign key fails fast; the
  `TokenStream` path passes the common params.
- Rejected: B (silent gap on the path Quarkus will use, unusable public setter), fail fast on an unbound tool
  (breaks framework contexts for no extra safety).

### Item 7: A, inferred in the `AiServiceTool` default; user agreed 2026-11-24

- Context: the Spring starters' `AiServiceFactory.aopEnhancedTools(..)` registers a proxied bean (`@Transactional`
  etc.) through `tools(Map)` with `new DefaultToolExecutor(proxy, originalToolMethod, enhancedToolMethod)`. The
  resolver already reads annotations from `originalMethod()` and its declaring class (~line 97 of
  `ToolGuardrailChainResolver`), but `AiServiceTool` defaults the source to `programmatic()` (line 41). So the same
  bean is `Local` or `Programmatic` depending on a proxy, in an attribute documented as trusted for security decisions
  (the `instanceof ToolSource.Local` docs example flips when `@Transactional` is added).
- **Rule:** an explicit `toolSource(..)` wins; otherwise a `DefaultToolExecutor` gives
  `ToolSource.local(originalMethod())` (the target's method, so the user's class name, not `$$SpringCGLIB$$`), and any
  other executor gives `programmatic()`. Done in the `AiServiceTool` default, so one place covers `tools(Map)`,
  `tools(List<AiServiceTool>)`, `ToolProvider` tools and item 6's on-demand binding. All new in 1.25.0, nothing
  released breaks. No langchain4j-spring change needed.
- Knock-on: item 1 gets the JDK-proxy rule (see item 1 notes).
- Docs: `tool-guardrails.md` `ToolSource.Programmatic` row (~line 472) and the paragraph at ~lines 478-481: a tool
  backed by a `DefaultToolExecutor` is `Local` however it is registered; Javadoc of `ToolSource.Programmatic` and
  `AiServiceTool.Builder.toolSource`.
- Tests: `tools(Map)` + `DefaultToolExecutor` is `Local` with the target class; the Spring-like shape (executor object
  a subclass "proxy", `originalMethod` the target's method) reports the target class name; an explicit
  `programmatic()` is kept; a lambda `ToolExecutor` stays `Programmatic`.
- Rejected: B (Spring only, waits for a starter release, leaves the trap for other frameworks and item 6), C (source
  depends on whether a proxy exists).

### Item 8: B with cleanup, user decided 2026-11-24

- Context: committing plans with the code is an experiment the user and Dmytro agreed to on Slack ("Let's
  experiment with it"; standardizing in `AGENTS.md` deferred until they have seen how it works; Dmytro later: "need
  to see more PRs in this style and compare"). PRs are squash-merged, so what is in the PR at merge time lands in
  `main`.
- **Decision: B, keep `.plans/` in the PR and merge it into `main`**, as the experiment intends ("committed with the
  code").
- Cleanup (part of the decision):
  - Scrub local paths and personal details: `PLAN.md` ~lines 501 and 511 (home directory, local clone path); grep
    the whole folder for others.
  - Trim session-state notes (for example "NEXT SESSION: `git stash pop` ...", stash / push bookkeeping, worktree
    paths): keep intent, decisions and outcomes, drop the notes meant only for the next agent session.
- No `.gitignore` change (standardization waits until after the experiment, per the Slack thread).
- Reply: the plans are part of an experiment we agreed to try; we scrubbed local paths and trimmed session-state
  notes. Do not quote the Slack thread on GitHub; just mention that it was discussed.
- **Fallback D, if Dmytro feels strongly about `main`:** keep `.plans/` in the PR for review and remove it in the
  last commit before merge (`git rm -r --cached .plans/`, files stay local; add `.plans/` to `.git/info/exclude`);
  the PR description links a commit permalink so D-number references keep working.
- Rejected: A / A' (remove now or move to a fork branch: ends the experiment on the PR it was started for), C (design
  doc under `docs/`: no project convention, `tool-guardrails.md` already covers users).

### Item 9: B, user decided 2026-11-24 (confirms task 6b)

- Already settled in task 6b: the setters on `ToolService` / `AiServices` / `AgentBuilder` were added at the user's
  request (`PLAN.md` ~line 532) so frameworks that provide their own config (Quarkus, Spring) can set it.
- **Precedent on `main`:** `DefaultInputGuardrailsConfig` has no settings either, yet `AiServices` and `AgentBuilder`
  have had `inputGuardrailsConfig(..)` since #2571 (2025-06). So the AI service guardrails already have "a setter
  with an empty default config"; B is parity, not a new pattern.
- The setters are also the home for the opt-in follow-ups from items 2 ("persist the rewrite") and 3 ("stop on the
  first failure") if they land as config settings.
- No code change. Reply: point to the `inputGuardrailsConfig(..)` precedent on `main` and the framework use case.
- Rejected: A (breaks parity with the AI service setters for a cosmetic gain; frameworks would lose the per-service
  way to pass a config).

### Item 10: A with a data-exposure warning, user decided 2026-11-24

- Context: `ToolService.collectCompensable` (~line 2128) builds one `ToolExecution` from `call.ranResult()` (before
  the output guardrails), and `fireCompensatedEvents` (~line 1801) uses it for the compensating action, the
  observability `ToolCompensatedEvent` and `AiServiceStreamingEvent.ToolCompensatedEvent`; `ToolExecutedEvent` gets
  the guarded result. Deliberate and documented today (`tool-guardrails.md` ~lines 563-569). Every compensation path
  (cancellation included) goes through `fireCompensatedEvents`.
- **Rule:** both compensation events carry the **guarded** result, the one the LLM saw before the rollback replaced
  it, the same as `ToolExecutedEvent` for that call (they already carry the rewritten request). The compensating
  action, and the agentic `onCompensableToolExecution`, keep the **raw** result (a guardrail may redact what the
  undo needs, e.g. a booking id).
- Edge: a tool whose own output guardrail turned it into an error: the compensation events carry that error result
  (as `ToolExecutedEvent` does); the action still gets the raw successful result.
- Not breaking: `ToolCompensatedEvent` is public since 1.20.0 ("original (successful) result"); without guardrails
  raw == guarded, so only the wording changes ("original" meant "before the rolled-back text", not "before the
  guardrails").
- Implementation: add the guarded `ToolExecution` to the `@Internal` record `CompensableToolExecution`; the action
  reads the raw one, `fireCompensatedEvents` the guarded one. No public API change.
- **Data-exposure documentation (user 2026-11-24):** with A, the tool guardrail events are the only events carrying
  data a guardrail later redacted. Each event's request holds what that guardrail checked (in a chain, only the first
  guardrail's is guaranteed raw); `ToolInputGuardrailExecutedEvent` likewise carries the arguments before an input
  rewrite.
  - `tool-guardrails.md` Observability: a `:::warning` under the intro paragraph: the tool guardrail events carry what
    the guardrail checked (arguments before an input rewrite, result before an output rewrite), the only events with
    data a guardrail later redacted; `ToolExecutedEvent` / `ToolCompensatedEvent` carry the guarded values; listeners
    exporting events to an external system should drop or redact the request of the tool guardrail events, or not
    register those listeners.
  - `tool-guardrails.md` security notes: a one-line bullet linking to the warning.
  - `observability.md` rows for the two tool guardrail events (~lines 43-44): "as it reached the guardrail (before
    that guardrail's rewrite)", linking to the warning.
  - Javadoc of `ToolInputGuardrailExecutedEvent` / `ToolOutputGuardrailExecutedEvent`: one sentence that the request
    holds the values before the guardrail's rewrite and can contain data it redacts.
  - No masking setting (it would defeat the event's purpose; AI service guardrail events carry the raw LLM response
    too).
- Other docs: rewrite the observability bullets in `tool-guardrails.md` (~lines 563-569: both events guarded, raw
  result in `ToolOutputGuardrailExecutedEvent`); keep the "compensating action gets the raw result" bullet (~line
  509); Javadoc of both `ToolCompensatedEvent` types; check the `observability.md` `ToolCompensatedEvent` row.
- Tests: a tool sanitized with `successWith(..)` and rolled back because another tool failed: both events carry the
  sanitized text, the action gets the raw result; a tool whose own output guardrail failed: events carry the error
  result, the action the raw one; the stream event on the `Flow.Publisher` path.
- Rejected: B (the two events of one call disagree; redacted data reaches the event family most often exported to
  telemetry), C (carry both results: new public API for a niche need, the raw result is already in the guardrail
  event).

### Item 11: A, user decided 2026-11-24

- Context (no guardrails, compensation on tool errors on; `credit` runs, `debit` runs, the `afterToolExecution`
  callback throws for `debit`):
  - `main`, **both** sync and `CompletableFuture`: the callback's exception records the call as failed, so `credit`
    is rolled back and `debit` stays; the `ToolExecutedEvent` for `debit` is an error with the callback's message.
    (Dmytro wrote only sync kept it; CF on `main` kept it too.)
  - Branch: both modes roll `debit` back (`ToolRound.settled()`, ~line 534: a failure recorded after the execution
    wins). But `ToolService.fireEventsOfFailedRound` (~line 2945) prefers the result over the error, so sync (and
    `TokenStream`, same `failBeforeRethrow` path) report `isError=false` with the tool's text, while CF reports the
    error. Two places answer one question differently: that is the bug.
  - `afterToolExecution` is a `ToolService` callback, so it applies in all 4 modes; `ToolCallbackFailureTest` covers
    only sync and CF.
- **Rule:** a call whose `afterToolExecution` callback throws ended as a failure: it is rolled back with the others
  that ran (a failing invocation undoes what ran, which is what opting into compensation promises), and its
  `ToolExecutedEvent` is an error with the callback's message in every mode (same event as `main`). Fits item 10's
  edge rule: its compensation events carry that error result.
- Implementation: `fireEventsOfFailedRound` builds its calls from the same "error wins" result as `settled()`, one
  rule in one place, so the two cannot drift again.
- Tests: extend `ToolCallbackFailureTest` to all 4 modes (with and without guardrails); assert the `ToolExecutedEvent`
  for `debit` is `isError=true` with "callback failed", followed by its `ToolCompensatedEvent`.
- PR description: drop "as on main"; add a Compatibility / Breaking Changes entry: when a throwing
  `afterToolExecution` fails the invocation, its tool is now rolled back too (on `main` it was kept in every mode).
- Reply: both `main` modes left the tool in place, not only sync.
- Rejected: B (restore `main`: keeps a half-done rollback, `credit` undone and `debit` kept, and needs code to throw
  away a result we have), A' (roll back but report the tool's success: changes the event in both modes compared with
  `main`, and the callback failure would not show in the events).

### Item 12: A, plus a key/request consistency check; user decided 2026-11-24

- Context: the framework builds requests only in `ToolGuardrailService.inputRequest` / `outputRequest` (~lines
  479-505), always from `key.toolSpecification()`; `ToolKey.toolSpecification()` is documented never null and
  `DefaultToolKey` enforces it; hallucinated tool names never reach a guardrail (`ToolService` ~lines 2786 and 3164);
  item 6's on-demand binding always has a spec from the context. So a null spec only comes from hand-built requests:
  unit tests, and frameworks (Quarkus after item 6) calling the public `executeInputGuardrails(ToolKey, request)` /
  `executeOutputGuardrails(..)`. Precedent: the AI service requests `ensureNotNull` their core fields
  (`userMessage`, `responseFromLLM`, `chatExecutor`).
- **Rule:** `toolSpecification` is required on `ToolInputGuardrailRequest` and `ToolOutputGuardrailRequest`: the
  builders `ensureNotNull` it, the accessor drops `@Nullable` and says "never null", the builder setter says
  "required". All new in 1.25.0, nothing released breaks.
- `toolSource` keeps its `programmatic()` default (the least trusted value, so leaving it out is safe).
- **Consistency check (the optional add-on, user 2026-11-24):** the four public entry points
  `executeInputGuardrails` / `executeInputGuardrailsAsync` / `executeOutputGuardrails` /
  `executeOutputGuardrailsAsync` (key + caller-built request) fail fast with an `IllegalArgumentException` when the
  request's `toolName()`, `toolSpecification()` or `toolSource()` is not equal to the key's (same style as the
  foreign-key check in `DefaultToolGuardrailService` ~line 122: thrown by the sync variants, fails the future of the
  async ones). Checked once on entry, not between guardrails of the chain (`withText` / rewrites keep the name, spec
  and source). Catches a framework that leaves the source out (a `Local` tool silently reported as `Programmatic`)
  or passes another tool's key. `ToolSpecification` and the `ToolSource` types already implement `equals`.
  - Javadoc of the four methods: the request must carry the key's tool name, specification and source;
    `@throws IllegalArgumentException` otherwise (sync), fails the future (async).
  - The internal path (`inputRequest` / `outputRequest` from the key) always passes the check.
- Rejected: B (null checks in every guardrail for a case that never happens at run time; a framework that forgets the
  spec goes unnoticed), C (a default spec from the request's name: a made-up spec with no description or parameters
  that looks real, and unlike the source there is no safe default).
- Docs: `tool-guardrails.md` unit-testing example (~line 627) adds
  `.toolSpecification(ToolSpecification.builder().name("fetch_page").build())`; the "built the same way" sentence
  (~line 651) mentions the spec is required; the extension-points / `ToolGuardrailService` part mentions the
  consistency check for frameworks.
- Tests: `ToolGuardrailRequestTest` lines 70, 226, 251 (null assertions) become "builder fails without a spec" tests
  for input and output; add the spec to the other hand-built test requests (helper if repeated); per entry point
  (sync and async, input and output): mismatched spec, source and name each fail fast / fail the future, a matching
  request runs.

### Item 13: B, user decided 2026-11-24

- Context: an input guardrail's `ToolArgumentsException` is caught in `ToolRound.execute` (~line 281), outside the
  service: the `argumentsError` handler's text is wrapped with `unguarded(..)`, the tool never started. The executor's
  own arguments error is caught inside the `tool` function (`ToolService.toolErrorResult`, ~line 3215) and comes back
  as an ordinary error result, which the output guardrails check like every error result of the tool.
- **Rule:** output guardrails check what the tool produced, success or error, including the text of its own error
  handlers (the executor's argument parsing counts as the tool running). Results made before the tool runs skip
  them: a non-fatal input guardrail failure, arguments an input guardrail could not parse, a hallucinated tool name.
  The line is "did the tool's executor start", not "where was the error detected".
- Same rule as the AI service guardrails: their output guardrails only see the model's response; an input failure
  (the model is never called) and an `IMMEDIATE` tool return resolved without the model skip them. Difference that
  exists in both options: a tool input failure / arguments error becomes an error result for the LLM, while an AI
  service input failure throws; and an executor error's handler text is output-guarded, while a throwing chat model
  is not (already a deliberate choice in the PR).
- Reasons against A: it is not "one rule" (input `failure(..)`, hallucinated names and fatal failures still skip the
  output guardrails); `ToolOutputGuardrailRequest.argumentsAsMap()` (core, line 99) parses the same unparseable
  arguments, so an output guardrail reading them would throw `ToolArgumentsException` again from the output stage
  and abort the invocation, turning recoverable bad JSON into a fatal error; it breaks D20 (either `ToolRound` calls
  the output guardrails itself, or the public `executeGuardrails(..)` / `executeGuardrailsAsync(..)` SPI gains an
  arguments-error handler parameter that Quarkus would build on). B's risk is low: the default handler's text comes
  from the LLM's own arguments; a custom handler is user code, like a `ToolExecutionErrorHandler`.
- Rejected the reverse fix (executor arguments errors skip the output guardrails too): the service cannot tell those
  results apart without new API, and it guards less.
- Docs: state the rule in the intro of "Tool Output Guardrails" in `tool-guardrails.md`; in "Arguments that cannot be
  parsed" (~line 107) add that the error text does not go through the output guardrails, while the same error from
  the tool's executor does (the executor ran).
- Javadoc: the `ToolArgumentsException` bullet of `ToolGuardrailService.executeGuardrails` (~line 223) adds "the
  output guardrails do not run"; same in the `ToolRound.execute` Javadoc (and the async counterparts).
- Tests, per mode: an input guardrail calling `argumentsAsMap()` on invalid JSON with a handler that sends the error
  to the LLM: the output guardrail is never called; counterpart where the executor throws the arguments error: the
  output guardrail sees the error result.
- Reply to Dmytro: the rule is "did the tool run", the same as the AI service guardrails (output guardrails check what
  the guarded thing produced); the main reason against A is that output guardrails would hit the same parse error.

### Item 14: B, user decided 2026-11-24

- Context: `Guardrail.validateAsync` (core, `@Experimental` since 1.20.0) defaults to a failed future carrying
  `AsyncNotSupportedException`, the "report not implemented through the future" pattern also used by `ChatMemory`,
  the RAG components, `WebSearchEngine` and `ToolExecutor`. In the `CompletableFuture` / `Flow.Publisher` modes a tool
  output guardrail without it fails only after the tool ran (a side effect, unlike an AI service output guardrail,
  where the late failure costs only an LLM call). `tool-guardrails.md` (~line 606) already says so.
- **Decision: B, no change.** Discovery stays by calling `validateAsync`; the failure is the fatal
  `ToolInputGuardrailException` / `ToolOutputGuardrailException` wrapping `AsyncNotSupportedException`, as documented.
- Rejected:
  - A (`supportsAsync()` on `Guardrail`): new core API on every guardrail with no workable default (`false` breaks
    every async guardrail released since 1.20, `true` tells nothing) and a second source of truth next to
    `validateAsync`.
  - C (proposed: an internal reflection pre-check at round start, "does the class override `validateAsync`"): user
    does not want reflection; extending it in Quarkus (guardrails are CDI beans behind generated subclasses / client
    proxies) would mean a rewrite there.
- Docs: optional polish only: `tool-guardrails.md` ~line 606 could add that in a round with several calls, other
  tools may already have started when an input guardrail's missing `validateAsync` is found. No Javadoc change.
- Reply to Dmytro: B; A adds core API with no safe default, and the same pattern applies everywhere since 1.20.

### Item 15: D (delete both ITs), user decided 2026-11-24

- Context: `AnthropicToolInputRewriteWithThinkingIT` and `GoogleAiGeminiToolInputRewriteWithThinkingIT` (the only
  provider-module changes of the PR) prove that a provider accepts a signed (thinking) turn whose tool call we edited
  in memory: they assert memory holds the rewritten call and the second request sends the signature with the rewritten
  arguments.
- Item 2 (B) removes that edit: memory keeps the LLM's original `AiMessage`, so the signed turn goes back unmodified.
  After item 2 the ITs fail as written (their core assertion is the opposite of the new behavior), and nothing
  provider-specific is left to test: providers do not check a tool result's content against the call's arguments, and
  signature replay with tool calls is covered by each module's own thinking ITs (`AnthropicChatModelThinkingIT`,
  `GoogleAiGeminiChatModelThinkingIT` and their streaming variants).
- **Decision: D, delete both ITs.** The PR then touches no provider module. Coverage of "memory holds the original
  call, the tool runs with the rewritten arguments" comes from item 2's per-mode unit tests (no API keys, every CI run).
- PR body (follow-ups, ~line 58): the bullet "keeping the original tool call in the chat memory as an `@Experimental`
  option ... see the opt-in ITs" flips: the original call is now the default; the follow-up is item 2's opt-in
  "persist the rewrite". If that opt-in lands, the ITs come back with it from git history.
- Reply to Dmytro: with item 2's B, memory never holds an edited signed turn, so the ITs have nothing left to test; we
  dropped them instead of moving them to new models. The model shutdown and adaptive thinking concern the provider
  modules' own thinking ITs, not this PR.
- Rejected: A (extra cost per opt-in run and a possible LangChain4j fix, to test an edit we no longer make), B / C
  (keep ITs that fail after item 2; C also tests another model than it names after 2026-12-01).

### Item 16: C, user decided 2026-11-24

- Context: `successWith(String)` takes raw JSON; building it by hand breaks on quotes (our own helper
  `ToolGuardrailTestSupport.appendToQuery` uses `"{\"q\":\"%s%s\"}".formatted(..)`). Guardrails read
  `request.argumentsAsMap()` but must write JSON back, and `dev.langchain4j.internal.Json` is `@Internal`, so plain
  LangChain4j users have no public helper. The API is unreleased: no compatibility concern.
- **Decision: C.** Add `successWithArguments(Map<String, ?>)` to `ToolInputGuardrail` (default method) and
  `ToolInputGuardrailResult` (static factory).
  - Serialized eagerly in the factory with `Json.toJson`, the same codec `argumentsAsMap()` parses with (Quarkus swaps
    it through the SPI, so both directions agree).
  - A value with no JSON representation throws inside `validate`, which `AbstractGuardrailExecutor.validate` (~line 113)
    already turns into a fatal `ToolInputGuardrailException` before the tool runs: fail closed, no extra code.
    `ensureValidRewrite` stays as the check for the `String` path.
  - `null` map is rejected (`ensureNotNull`), unlike `successWith(String)` where `null` means no rewrite: a redaction
    guardrail returning `null` by mistake must not silently pass the original arguments. Javadoc states the difference.
  - `Map<String, ?>` so a `Map<String, String>` is accepted.
  - Naming follows `OutputGuardrail.successWithResult(Object)` and pairs with `argumentsAsMap()`.
- Follow-ups: Javadoc on both methods; `tool-guardrails.md` input results table (~line 102) gets a row, and the rewrite
  example uses the `Map` form; `appendToQuery` moves to the new method; tests per mode: a value with quotes reaches the
  tool intact, an unserializable value is a fatal `ToolInputGuardrailException` and the tool never runs, `null` is
  rejected. (The stale memory sentence in the `successWith(String)` Javadoc is item 2's follow-up.)
- Reply to Dmytro: C; A's overload makes `successWith(null)` ambiguous, B leaves every user without a public JSON
  helper.
- Rejected: A (ambiguous `successWith(null)`), B (plumbing for everyone, no public JSON helper in plain LangChain4j).

## Dmytro's Answer (2026-11-25)

[Comment 7160257905](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7160257905) and
[comment 7174145296](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7174145296).

### Approvals

- Item 5: the split is fine, **as long as the `TokenStream` follow-up lands before the 1.25.0 release**, so no
  release has a different cancellation rule per mode.
- Item 8: keep `.plans/` in `main` after the cleanup; no removal at the end.
- Items 1, 15 and 16: our versions.
- Everything else: go ahead as written.
- 250d83199 (cancel pending output guardrails) looks good to him.

### Docs fixes (accepted as is, user 2026-11-25)

- Item 3: the note under the failure rows must not say "order matters". The chain keeps running, so every guardrail
  gets content an earlier one rejected, whether it runs before or after it. Only `fatal(..)` stops the chain, and only
  redacting with `successWith(..)` (or `successWithArguments(..)`) keeps the content from the later guardrails. Order
  matters only for rewrites (a later guardrail sees the earlier rewrite).
- PR description, Compatibility section: since a17bef6eb, a tool call whose arguments are the JSON literal `null` is
  read like `{}`, also without tool guardrails. On `main`, a tool with parameters failed with a `ToolArgumentsException`
  caused by a `NullPointerException`. Add a line. Rework the section into one list, with the item 5 (cancellation) and
  item 11 (`afterToolExecution` rollback) entries.

### Item 2 addition: a note in the tool result after an input rewrite

- Problem (we missed it in item 2): with B, memory holds the model's original call (`amount = 10000`) next to the
  result of the rewritten one (`amount = 1000`, "Transfer done"), so the model believes on every later turn that the
  original call ran, and may tell the user so.
- His proposal: keep B; when an input guardrail changed the arguments, the tool result text the model gets starts with
  a note in its own tag, then the tool's result unchanged:
  `<note>The arguments of this call were changed before it ran: {"amount": 1000}</note>\nTransfer done`.
  - Same text, not a separate content: the OpenAI Chat Completions mapper rejects tool results with more than one
    content, and `ToolExecutionResultMessage.text()` throws unless there is exactly one `TextContent` (checked).
  - Overridable text, a way to turn it off, a template with the arguments and the result as placeholders.
  - Events and `toolExecutions()` keep the plain result.
  - No signed-turn edit, no extra memory write: item 2 and item 15 stay as they are.
  - In this PR, or a follow-up that lands before 1.25.0 (the release ships with the note on by default). Tag and
    placeholder names are up to us.
- **Position (user agreed 2026-11-25): accept, in this PR.** It completes item 2, touches the code item 2 rewrites
  anyway (where `ToolRound` builds the result message), and two must-land-before-1.25.0 follow-ups double the release
  risk.
- Details we settle (raise 1, 2 and 5 with him, accept the rest):
  1. **No text content.** A result with only non-text contents (for example one `ImageContent`) gets no note: adding a
     text content would break OpenAI and `text()`. DEBUG log, documented. Otherwise the note goes into the first
     `TextContent`.
  2. **Arguments a guardrail added.** A guardrail that enriches the arguments with server-side values (tenant id, user
     id, a token from the invocation context) would leak them into the LLM context and the stored memory through the
     default note. Keep his default (arguments in the note, which the `transferMoney` case needs) plus a security
     note in `tool-guardrails.md` to change the template or turn the note off for such guardrails; ask him whether
     he'd rather default to a note without the arguments.
  3. **Large arguments.** A rewritten document-sized argument is repeated in the context on every later turn;
     documented, the template covers it.
  4. **Where it's added.** When the `ToolExecutionResultMessage` is built, after the output guardrails (they check the
     tool's result, not our note). Also on error results, and kept when a compensation replaces the result.
     `toolExecutions()`, the events and the `IMMEDIATE` `String` return (item 4) keep the plain result.
  5. **Template substitution.** One pass over the template, so a tool result containing the arguments placeholder is
     never expanded (check how `DefaultPromptTemplateFactory` replaces variables before reusing `PromptTemplate`;
     otherwise a small dedicated formatter). Tested either way.
  6. **Setting.** On `ToolInputGuardrailsConfig` (its first real setting, which also backs item 9): the template, and a
     way to turn it off. On by default; tool guardrails are new in 1.25.0, so nothing released changes.
  7. **Spoofing.** A tool's own result can contain a fake note tag; minor, a specific tag name helps. Tag:
     `<tool_arguments_changed>`? (open, up to us).
- Knock-on: the Quarkus extension (#3162) adds no such note today; it gets it once it moves to the core resolution.

### Dmytro's answer on the note (2026-11-26)

[Comment 7189169590](https://github.com/langchain4j/langchain4j/pull/7202#issuecomment-7189169590): (1) no text: skip,
but WARN once per tool; (2) the default note leaves added arguments out: only the top-level arguments the model sent
whose values changed, with their new values (a removed one as `null`), and the security note stays for a guardrail that
overwrites a model argument with a secret; (3) names OK, `{{arguments}}` holds only the changed arguments; (4) decide
"changed" on parsed values, so `successWithArguments(..)` re-serialization does not add an untrue note. Implemented in
task 22, step 11.
