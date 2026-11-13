Integration tests for the AI Service capture and assertions of [`langchain4j-test`](../../../langchain4j-test), in a Spring Boot application.

The application is a small customer-support assistant built without the LangChain4j Spring Boot starter: the AI Services are
created with `AiServices.builder(...)` in a `@Configuration`, from model beans. It has `@Tool` methods on Spring beans (one of
them behind an AOP proxy), a content retriever, an output guardrail, a REST controller, a server-sent events endpoint that starts
a `TokenStream` on a `TaskExecutor`, a `Flux` endpoint and an `@Async` service.

The application declares no models: the tests provide them as beans (`TestModels`), using the mocks of the `langchain4j-core`
test-jar. The tests check what is captured, and what is not, when the AI Services are called from tests with `MockMvc`, a running
server (`RANDOM_PORT`), `@Async` methods, `@MockitoBean` mocks, a `@WebMvcTest` slice, reactive types, parallel tests and several
Spring application contexts.
