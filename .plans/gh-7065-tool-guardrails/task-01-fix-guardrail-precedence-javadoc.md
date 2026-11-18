# Task 01: (Separate PR) Fix the Inverted Guardrail Precedence Javadoc

**Type:** Documentation / Javadoc fix. **Separate PR, branched from `main`**, not part of the tool
guardrails PR.

> **Status (2026-11-13):** done, but our PR [#7109](https://github.com/langchain4j/langchain4j/pull/7109) was closed unmerged in favor of [#7088](https://github.com/langchain4j/langchain4j/pull/7088) (@PSchmitz-Valckenberg),
> which contains the same fix, our extra parts, and @dliubarskyi's review fixes. Track [#7088](https://github.com/langchain4j/langchain4j/pull/7088) in PLAN.md → "Upstream
> Dependencies". Nothing is left to do here.

## Goal

Make the Javadoc of `GuardrailServiceBuilder` and the `AiServices` guardrail builder methods match the
actual (and documented in `guardrails.md`) precedence: **`AiServices` builder > method annotation >
class annotation**. @dliubarskyi reported this in
https://github.com/langchain4j/langchain4j/discussions/7030#discussioncomment-19538533. Tool guardrails
(D6) mirror this precedence, so the reference has to be correct.

## What to Do

- Create a branch from `origin/main` (not from the tool guardrails branch).
- Fix the class Javadoc in `GuardrailServiceBuilder` (lines ~28-30), which currently says "annotations take
  precedence over guardrails configured programmatically in the builder".
- Fix every `AiServices` guardrail builder method Javadoc that says "Using the annotation takes precedence"
  (`inputGuardrailsConfig`, `outputGuardrailsConfig`, `inputGuardrailClasses` ×2, `inputGuardrails` ×2,
  `outputGuardrailClasses` ×2, `outputGuardrails` ×2; ~lines 1019-1300). Also check the wording "Configuring
  this way is exactly the same as using the annotation at the class level", which is wrong for the same reason.
- Fix the adjacent typo `InptputGuardrails` (~line 1043).
- Scan for other places with the same wrong claim: `grep -rn "takes precedence" langchain4j/src/main/java/dev/langchain4j/service`,
  the `InputGuardrails` / `OutputGuardrails` annotation Javadoc, and `docs/docs/tutorials/guardrails.md` (the docs
  are believed correct; verify).
- Do not change any behavior.

## Files/Areas

- `langchain4j/src/main/java/dev/langchain4j/service/guardrail/GuardrailServiceBuilder.java`
- `langchain4j/src/main/java/dev/langchain4j/service/AiServices.java`
- Possibly `InputGuardrails.java` / `OutputGuardrails.java` Javadoc

## Key Points

- The source of truth is `GuardrailServiceBuilder.computeInputGuardrailsForAiServiceMethod` /
  `computeOutputGuardrailsForAiServiceMethod` (~lines 268-305): builder first, then method, then class.
- Check whether any existing test pins this precedence (e.g. `InputGuardrailOnClassAndMethodTest`,
  `OutputGuardrailOnClassAndMethodTests`). If no test pins "builder beats annotations", consider adding one
  small test so the Javadoc can't drift again. Confirm with the user first, since this is a Javadoc-only PR.
- Javadoc-only, so no revapi impact.

## Done When

- [X] All Javadoc in `GuardrailServiceBuilder` / `AiServices` states builder > method > class.
- [X] Typo `InptputGuardrails` fixed.
- [X] No other source states the inverted precedence.
- [X] `mvn -Pspotless validate` at the repo root passes.
- [X] The user has reviewed. Commit and PR prepared separately (PR body references discussion [#7030](https://github.com/langchain4j/langchain4j/discussions/7030) and [#7065](https://github.com/langchain4j/langchain4j/issues/7065);
      no closing keywords in the title).
