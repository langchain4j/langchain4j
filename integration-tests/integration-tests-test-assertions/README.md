Tests of the testing support of `langchain4j-test` (capture AI Service invocations, assertions on them, and judge
assertions), used the way a user would use it in plain Java, without a framework.

`src/main/java` contains a small customer-support application built with AI Services: tools, RAG, chat memory,
guardrails, structured output, streaming and asynchronous methods, and an AI Service used as a tool by another one.
`src/test/java` tests it with `AiServiceCapture`, `LangChain4jAssertions` and `Judge`, using
the mock models of `langchain4j-test`.

One test class runs its tests in parallel (see `src/test/resources/junit-platform.properties`), to check that
captures on different threads do not see each other's invocations.

The same checks with Spring Boot and Quarkus are in the submodules.

Two more submodules check the AI Services that the frameworks create for you, from an annotated interface:

- `integration-tests-test-assertions-spring-starter`: `@AiService` interfaces of the LangChain4j Spring Boot starter
  (`langchain4j-spring-boot-starter`), with `@Tool` methods on a Spring bean, a content retriever bean, a chat memory
  provider bean and streaming methods returning `TokenStream` and `Flux`.
- `integration-tests-test-assertions-quarkus-extension`: `@RegisterAiService` interfaces of the Quarkus LangChain4j
  extension (`quarkus-langchain4j-core`), with tools, a retrieval augmentor, chat memory and streaming methods returning
  `Multi` and `TokenStream`. The AI Services are declared in `src/test/java`, because they get their mock models from
  `chatLanguageModelSupplier` and `streamingChatLanguageModelSupplier` classes, which live next to the mocks. Some tests
  check what the extension does not report yet (whether a tool failed, the retrieved sources, the events of a
  `TokenStream`): they show the message that `langchain4j-test` gives in this case.

Both use the released starter and extension, on the LangChain4j version of this build. Because a deliberate change in
LangChain4j can break these released versions until they are released again, the two modules are built only with the
Maven profile `test-assertions-frameworks` (`-Ptest-assertions-frameworks`), which the nightly builds turn on, so that
they never block other builds. Their versions are Maven
properties in the module poms (`langchain4j-spring.version` and `spring-boot.version`, `quarkus-langchain4j.version`
and `quarkus.version`), pinned to released versions. The nightly build (`latest-frameworks` job in
`.github/workflows/nightly_jdk17.yaml`) also runs both modules on the latest releases from Maven Central, by setting
these properties with `-D`, so a new release that does not work with the current LangChain4j is noticed early.
