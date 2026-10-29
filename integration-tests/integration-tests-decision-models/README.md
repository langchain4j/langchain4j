# Decision model integration tests

Tests of the LangChain4j components that use a `DecisionModel` (Decision Services, guardrails, tool search, etc.),
run against a real decision model: TypeSafe Jev.

They run only when the `TYPESAFE_API_KEY` environment variable is set.
The unit tests of these components live in their own modules and use `DecisionModelMock`.
