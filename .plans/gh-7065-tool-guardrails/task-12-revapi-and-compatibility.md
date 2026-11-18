# Task 12: Revapi Justifications and Compatibility Review

**Type:** Build / API compatibility

> **Before you start:** pull the latest from upstream `main` and check the in-flight PRs in PLAN.md →
> "Upstream Dependencies" (Execution Step 2). Most relevant to this task: all of them (the revapi baseline moves with `main`).

## Goal

Make the revapi check pass for `langchain4j-core`, `langchain4j`, `langchain4j-mcp` and `langchain4j-agentic`, with accurate
justifications, and produce a compatibility summary for the PR body.

## What to Do

1. Run the revapi check for the affected modules (find the exact invocation in `langchain4j-parent/pom.xml`; the
   revapi plugin runs in the standard build), and collect every reported difference.
2. Expected categories (verify; don't assume):
   - new permitted subtypes on sealed types (`GuardrailRequest`, `GuardrailResult`, `GuardrailResult.Failure`,
     `GuardrailException`, `GuardrailExecutor` / `AbstractGuardrailExecutor` / its builder,
     `GuardrailExecutedListener`, `GuardrailExecutorBuilderFactory`). Revapi doesn't model sealing; justify these
     as "sealed; only permits are added inside langchain4j-core".
   - the new protected `rewriteRequest` hook on `@Internal` `AbstractGuardrailExecutor`
   - new `CompensationReason` enum constants (the enum is `@Experimental`; say so in the justification)
   - new methods on `AiServices`, `AiServiceTool` / its builder, `AgentBuilder` (`langchain4j-agentic`), and
     possibly `ToolService` overloads
3. Add justifications to `langchain4j-core/revapi.json`, `langchain4j/revapi.json` and (if needed)
   `langchain4j-mcp/revapi.json` / `langchain4j-agentic/revapi.json`, following the existing wording style (see the `GuardrailExecutor::executeAsync`
   and `OutputGuardrailExecutor::composeResult` entries).
4. Write a short **compatibility summary** in PLAN.md for the PR body:
   - source-compatible for normal use; **may break exhaustive `switch`es** over the extended sealed types and over
     `CompensationReason` in user code (none exist in this repo; verify with `grep`)
   - no changes to `@InputGuardrails` / `@OutputGuardrails` / `InputGuardrail` / `OutputGuardrail`
   - behavior is unchanged when no tool guardrails are configured
   - everything new is `@Experimental` (D19), **except** `GenericGuardrailService` (+ its `Builder`), which the stable
     `GuardrailService` extends, and `ClassMetadataProviderFactory#resolveMethodKey` (user decision, 2026-11-17)
   - the guardrail service hierarchy (task 06a): `GenericGuardrailService` / `GenericGuardrailService.Builder` are new
     and sealed; `GuardrailService` / `GuardrailService.Builder` became `non-sealed` and redeclare their methods for
     binary compatibility (removing a redeclaration fails revapi with `java.method.*TypeErasureChanged`, verified
     2026-11-17); `AbstractGuardrailService` and `GuardrailService#aiServiceClass()` are deprecated; frameworks that
     subclass `AbstractGuardrailService` or implement `GuardrailService.Builder` (CDI, Micronaut) keep working
5. Check whether Quarkus-relevant SPI types (`*ExecutorBuilderFactory`) changed in a way that affects
   quarkus-langchain4j, and note it for [quarkiverse/quarkus-langchain4j#3162](https://github.com/quarkiverse/quarkus-langchain4j/issues/3162).

## Files/Areas

- `langchain4j-core/revapi.json`, `langchain4j/revapi.json`, `langchain4j-mcp/revapi.json`,
  `langchain4j-agentic/revapi.json`
- `langchain4j-parent/pom.xml` (revapi configuration)

## Key Points

- Justify only real differences. Do not add blanket ignores.
- If revapi flags something unexpected (e.g. a changed public signature), fix the code rather than justifying it.

## Done When

- [x] Revapi passes for all affected modules (core, langchain4j, langchain4j-mcp, langchain4j-agentic; four runs, see Outcome).
- [x] Every justification is specific and accurate (no new one was needed; the four from task 06 are still required and accurate).
- [x] The compatibility summary is recorded in PLAN.md (`### Compatibility Summary`).

## Outcome

**Complete; reviewed by the user, committed as `6625feee6` and pushed to the fork, 2026-11-18.**

**Result: no new revapi justification was needed.** Revapi passes for the four modules with the four `externalClassExposedInAPI` entries
that task 06 added to `langchain4j-agentic/revapi.json` and nothing else. The categories the task expected do not show up:

| Expected category | Why revapi is silent |
|---|---|
| New permitted subtypes of the sealed types | Revapi does not model sealing, and the new permitted subtypes are new classes, which are not API breaks. |
| Protected `AbstractGuardrailExecutor#rewriteRequest` | `AbstractGuardrailExecutor` is `@Internal`, so the parent POM's filter excludes it. |
| New `CompensationReason` constants | Adding enum constants is not flagged by revapi. |
| New methods on `AiServices`, `AiServiceTool` (+ builder), `ToolService` overloads | Additive on classes and builders, no new abstract method on a public interface. |
| New default methods on `GuardrailService`, `ClassMetadataProviderFactory` | Default methods are not `addedToInterface`. |

Not adding a justification for something revapi does not report is deliberate (task Key Points: "justify only real differences").
The sealed-type and enum effects are source-compatibility facts, so they go in PLAN.md's compatibility summary and the PR body.

**What ran (all on the feature branch at `517946047` + this task's edits, against the released `1.24.0`):**

| # | Run | Result |
|---|---|---|
| 1 | Default build (`oldVersion=RELEASE`), core + langchain4j + mcp + agentic, `install` | `RELEASE` resolves to 1.24.0 in the local repo's `maven-metadata`, so it compared 1.23.0 vs the module's own 1.24.0 version, which is **not** the check CI will run after the next release. Passed, but proves little. |
| 2 | `revapi:check -Drevapi.oldArtifacts=dev.langchain4j:<m>:<1.24.0 or 1.24.0-beta34>` per module, after clearing the locally installed 1.24.0 artifacts | Compared against 1.24.0 from Maven Central: "API checks completed without failures" for all four. |
| 3 | A throwaway worktree with every POM bumped to `1.25.0-SNAPSHOT` / `1.25.0-beta35-SNAPSHOT`, `verify` of the four modules and their dependencies (`-am`) | This is the real post-release check (1.24.0 baseline, our work as 1.25.0): BUILD SUCCESS, "API checks completed without failures" for every module. The worktree is removed. |
| 4 | Removed the four agentic justifications temporarily (restored afterward), same baseline | Fails with exactly the four `java.class.externalClassExposedInAPI` (the two guardrail interfaces, the two configs). They are still needed, and their wording is accurate. |

`langchain4j-mcp` run 2 needs the `-Drevapi.oldArtifacts` form: without a resolvable baseline, revapi reports `org.slf4j.Logger` exposed via
`McpLoggers`, which is a pre-existing artifact of comparing against nothing.

**One real fix found: every `@since 1.24.0` was wrong.** Upstream released 1.24.0 on 2026-11-18 without this work. The repository's
`langchain4j-core` is at `1.24.0`, and the baseline is now that release, so the work ships in 1.25.0. All 95 `@since 1.24.0` tags and 3
`@Deprecated(since = "1.24.0")` (`AbstractGuardrailService`, `GuardrailService#aiServiceClass()`, `ToolExecutionRequestUtil`) in the 41 files
this branch changes versus `upstream/main` are now `1.25.0` (this was the follow-up noted under task 10). Only lines this branch added were
touched (found from `git diff upstream/main -U0`; the tags that upstream itself added for 1.24.0 are untouched). The plan and task 02 text that
quoted the old version are updated too.

**Quarkus (`quarkiverse/quarkus-langchain4j#3162`) check, read from `quarkiverse/quarkus-langchain4j` `main` (it builds on `langchain4j.version` 1.24.0):**
- It uses `GuardrailService#executeGuardrails` (`GuardrailsSupport`) and registers `InputGuardrailsConfigBuilderFactory` /
  `OutputGuardrailsConfigBuilderFactory` and `ClassMetadataProviderFactory` services. It provides no `*GuardrailExecutorBuilderFactory`, does not extend
  `AbstractGuardrailService` and does not implement `GuardrailService.Builder` (code search came back empty for each). The sealed `GuardrailExecutorBuilderFactory`
  gaining two permitted subtypes therefore changes nothing for it.
- Its `QuarkusClassMetadataProviderFactory` keys methods by `AiServiceMethodCreateInfo`, not by `Method`. It inherits the default
  `resolveMethodKey` (identity, a cast), which is right only for `Method` keys. Whether that breaks anything depends on how Quarkus adopts the new
  per-tool / per-method resolution (read from its source, **not run**), so it probably needs to override it then. Suggested for #3162 as an item
  next to the deprecation of its own tool guardrail API; I did not post to the issue, and that is the user's call.
