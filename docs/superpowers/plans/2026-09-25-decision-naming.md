# Decision naming update

## Goal

Apply the name agreed in issue #6468 to the two new PRs only. Keep the original PR branches untouched.

## Steps

1. Add a consumer-facing core test importing `dev.langchain4j.model.decision.DecisionModel`, `DecisionRequest`, and `DecisionResponse`; run it to observe RED.
2. Rename the core package, public types, artifact/module, tests, and relevant documentation to `Decision`; run the core tests and install its snapshot locally.
3. Add a consumer-facing community test using `SystemOneDecisionModel` and `TypeSafeDecisionModel`; run it to observe RED.
4. Rename community adapters and imports, update the core dependency, and run both affected module test suites.
5. Verify no stale API names remain in source, check formatting and diffs, then commit and push only the two new PR branches. Update their titles and descriptions; leave the original PRs alone.
