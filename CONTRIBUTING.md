Thank you for investing your time and effort in contributing to our project, we appreciate it a lot! 🤗

# General guidelines

- For new integrations, please consider adding it in [community repo](https://github.com/langchain4j/langchain4j-community) first.
- If you want to contribute a bug fix or a new feature that isn't listed in the [issues](https://github.com/langchain4j/langchain4j/issues) yet, please open a new issue for it. We will triage is shortly.
- Follow [Google's Best Practices for Java Libraries](https://jlbp.dev/)
- Keep the code compatible with Java 17.
- When integrating third-party services, use the official SDK whenever possible. If no official SDK is available, implement the client using `langchain4j-http-client` and Jackson.
- Avoid adding new dependencies as much as possible (new dependencies with test scope are OK). If absolutely necessary, try to use the same libraries which are already used in the project. Make sure you run `mvn dependency:analyze` to identify unnecessary dependencies.
- Write unit and/or integration tests for your code. This is critical: no tests, no review!
- The tests should cover both positive and negative cases.
- Make sure you run all unit tests on all modules with `mvn clean test`
- Avoid making breaking changes. Always keep backward compatibility in mind. For example, instead of removing fields/methods/etc, mark them `@Deprecated` and make sure they still work as before.
- Follow existing naming conventions.
- Add Javadoc where necessary. There's no need to duplicate Javadoc from the implemented interfaces.
- Follow existing code style present in the project. Run `make lint` and `make format` before commit.
- Large features should be discussed with maintainers before implementation.

# Opening an issue

- Please fill in all sections of the issue template.

# Opening a draft PR

- Please open the PR as a draft initially. Once it is reviewed and approved, we will then ask you to finalize it (see section below).
- Fill in all the sections of the PR template.
- Please make it easier to review your PR:
  - Keep changes as small as possible.
  - Do not combine refactoring with changes in a single PR.
  - Avoid reformatting existing code.

Please note that we do not have the capacity to review PRs immediately. We ask for your patience. We are doing our best to review your PR as quickly as possible.

# Finalizing the draft PR

- Add [documentation](https://github.com/langchain4j/langchain4j/tree/main/docs/docs) (if required).
- Add an example to the [examples repository](https://github.com/langchain4j/langchain4j-examples) (if required).
- Run `./mvnw spotless:check` and `./mvnw spotless:apply` to ensure compliance with the source code formatting of the project.
- [Mark a PR as ready for review](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/changing-the-stage-of-a-pull-request#marking-a-pull-request-as-ready-for-review)

# Guidelines on adding a new model integration

- Please open PRs with new model integrations in the [langchain4j-community](https://github.com/langchain4j/langchain4j-community) repository
- [Integration with OpenAI](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-open-ai) is a good example.
- Create integration test classes that extend from [`AbstractChatModelIT`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/test/java/dev/langchain4j/model/chat/common/AbstractChatModelIT.java), [`AbstractStreamingChatModelIT`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/test/java/dev/langchain4j/model/chat/common/AbstractStreamingChatModelIT.java), [`AbstractChatModelListenerIT`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/test/java/dev/langchain4j/model/chat/common/AbstractChatModelListenerIT.java), [`AbstractStreamingChatModelListenerIT`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/test/java/dev/langchain4j/model/chat/common/AbstractStreamingChatModelListenerIT.java) and [`AbstractStreamingAiServiceIT`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/common/AbstractStreamingAiServiceIT.java). There are many examples in existing modules.
- If model provider supports tools, create an integration test class that extends from [`AbstractAiServiceWithToolsIT`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/common/AbstractAiServiceWithToolsIT.java). There are many examples in existing modules.
- If model provider supports structured outputs, create an integration test class that extends from [`AbstractAiServiceWithJsonSchemaIT`](https://github.com/langchain4j/langchain4j/blob/main/langchain4j/src/test/java/dev/langchain4j/service/common/AbstractAiServiceWithJsonSchemaIT.java). There are many examples in existing modules.
- Document the new integration [here](https://github.com/langchain4j/langchain4j/blob/main/README.md), [here](https://github.com/langchain4j/langchain4j/tree/main/docs/docs/integrations/language-models) and [here](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/language-models/index.md).
- Add an example to the [examples repository](https://github.com/langchain4j/langchain4j-examples), similar to [this](https://github.com/langchain4j/langchain4j-examples/tree/main/anthropic-examples).
- Add a new module to the appropriate section of the [BOM](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-bom/pom.xml).
- It would be great if you could add a [Spring Boot starter](https://github.com/langchain4j/langchain4j-spring).

# Guidelines on adding a new embedding store integration

- Please open PRs with new embedding store integrations in the [langchain4j-community](https://github.com/langchain4j/langchain4j-community) repository
- [Integration with Chroma](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-chroma) is a good example.
- Add a `{IntegrationName}EmbeddingStoreIT`. It should extend from `EmbeddingStoreWithFilteringIT` (when store supports metadata filtering) or `EmbeddingStoreIT` and pass all tests.
- Add a `{IntegrationName}EmbeddingStoreRemovalIT`. It should extend from `EmbeddingStoreWithRemovalIT` and pass all tests.
- Document the new integration [here](https://github.com/langchain4j/langchain4j/blob/main/README.md), [here](https://github.com/langchain4j/langchain4j/tree/main/docs/docs/integrations/embedding-stores) and [here](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/embedding-stores/index.md).
- Add an example to the [examples repository](https://github.com/langchain4j/langchain4j-examples), similar to [this](https://github.com/langchain4j/langchain4j-examples/tree/main/chroma-example).
- Add a new module to the appropriate section of the [BOM](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-bom/pom.xml).
- It would be great if you could add a [Spring Boot starter](https://github.com/langchain4j/langchain4j-spring). (after

# Guidelines on changing an existing embedding store integration

- Ensure that your changes are backwards compatible. `Embedding`s and `TextSegment`s persisted with the latest released version of LangChain4j should still work.
