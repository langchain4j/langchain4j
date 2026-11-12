Integration tests for the testing support of [`langchain4j-test`](../../../langchain4j-test) (`AiServiceCapture`,
`LangChain4jAssertions`, `Judge`) in a Quarkus application, written from the point of view of a Quarkus user.

The application (`src/main/java/com/example/support`) is a customer-support assistant that does not use the
quarkus-langchain4j extension: its AI Services are created with `AiServices.builder(...)` in CDI producer methods, with
`@Tool` methods on `@ApplicationScoped` and `@RequestScoped` beans, a content retriever bean, an output guardrail bean,
and a REST resource that returns `String`, `Uni<String>` and `Multi<String>`.

The models are CDI beans. In the tests they are produced by `TestModels` and replaced per test with
`QuarkusMock.installMockForType(...)` by the shared mocks of langchain4j-core (`ChatModelMock`,
`StreamingChatModelMock`, `DecisionModelMock`). Because the application gets its models from a model provider that is
not on the classpath here, the module has no `quarkus:build` goal: only `@QuarkusTest` starts the application.
