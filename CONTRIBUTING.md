Thank you for investing your time and effort in contributing to our project, we appreciate it a lot! 🤗


# Current situation (25 April 2024)
There are over 60 open PRs. Please help us by reviewing them first, before opening new ones. 🙏


# General guidelines
- If you want to contribute a bug fix or a new feature that isn't listed in the [issues](https://github.com/langchain4j/langchain4j/issues) yet, please open a new issue for it. We will prioritize is shortly.
- Follow [Google's Best Practices for Java Libraries](https://jlbp.dev/)
- Keep the code compatible with Java 8. We plan to increase the baseline to Java 17 a bit later.
- Avoid adding new dependencies as much as possible. If absolutely necessary, try to use the same libraries which are already used in the project.
- Write unit and/or integration tests for your code. This is critical: no tests, no review!
- Avoid making breaking changes. Always keep backward compatibility in mind.
- Follow existing naming conventions.
- Add Javadoc where necessary. There's no need to duplicate Javadoc from the implemented interfaces.
- Follow existing code style present in the project.
- Large features should be discussed with maintainers before implementation. Please ping @langchain4j in the comments on the issue.


# Priorities
All [issues](https://github.com/langchain4j/langchain4j/issues) are prioritized by maintainers. There are 4 priorities: [P1](https://github.com/langchain4j/langchain4j/issues?q=is%3Aissue+is%3Aopen+label%3AP1), [P2](https://github.com/langchain4j/langchain4j/issues?q=is%3Aissue+is%3Aopen+label%3AP2), [P3](https://github.com/langchain4j/langchain4j/issues?q=is%3Aissue+is%3Aopen+label%3AP3) and [P4](https://github.com/langchain4j/langchain4j/issues?q=is%3Aissue+is%3Aopen+label%3AP4).

Please start with the higher priorities. PRs will be reviewed in order of priority, with bugs being a higher priority than new features.

Please note that we do not have the capacity to review PRs immediately. We ask for your patience. We are doing our best to review your PR as quickly as possible.


# Opening an issue
- Please fill in all sections of the issue template.


# Opening a draft PR
- Please open the PR as a draft initially. Once it is reviewed and approved, we will then ask you to finalize it (see section below).
- Fill in all the sections of the PR template.
- Please make it easier to review your PR:
  - Keep changes as small as possible.
  - Do not combine refactoring with changes in a single PR.
  - Avoid reformatting existing code.


# Finalizing the draft PR
- Add [documentation](https://github.com/langchain4j/langchain4j/tree/main/docs/docs) (if required).
- Add an example to the [examples repository](https://github.com/langchain4j/langchain4j-examples) (if required).
- [Mark a PR as ready for review](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/changing-the-stage-of-a-pull-request#marking-a-pull-request-as-ready-for-review)


# Guidelines on adding a new model integration
- [Integration with Anthropic](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-anthropic) is a good example.
- Use the official SDK if available.
- If the official SDK is not available, use Retrofit and Gson to implement the client.
- Document the new integration [here](https://github.com/langchain4j/langchain4j/blob/main/README.md), [here](https://github.com/langchain4j/langchain4j/tree/main/docs/docs/integrations/language-models) and [here](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/language-models/index.md).
- Add an example to the [examples repository](https://github.com/langchain4j/langchain4j-examples), similar to [this](https://github.com/langchain4j/langchain4j-examples/tree/main/anthropic-examples).
- Add a new module to the appropriate section of the [BOM](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-bom/pom.xml).
- It would be great if you could add a [Spring Boot starter](https://github.com/langchain4j/langchain4j-spring).


# Guidelines on adding a new embedding store integration
- [Integration with Chroma](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-chroma) is a good example.
- Use the official SDK if available.
- If the official SDK is not available, use Retrofit and Gson to implement the client.
- Add a `{IntegrationName}EmbeddingStoreIT`. It should extend from `EmbeddingStoreWithFilteringIT` or `EmbeddingStoreIT` and pass all tests.
- Document the new integration [here](https://github.com/langchain4j/langchain4j/blob/main/README.md), [here](https://github.com/langchain4j/langchain4j/tree/main/docs/docs/integrations/embedding-stores) and [here](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/embedding-stores/index.md).
- Add an example to the [examples repository](https://github.com/langchain4j/langchain4j-examples), similar to [this](https://github.com/langchain4j/langchain4j-examples/tree/main/chroma-example).
- Add a new module to the appropriate section of the [BOM](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-bom/pom.xml).
- It would be great if you could add a [Spring Boot starter](https://github.com/langchain4j/langchain4j-spring). (after 


# Guidelines on changing an existing embedding store integration
- Ensure that your changes are backwards compatible. `Embedding`s and `TextSegment`s persisted with the latest released version of LangChain4j should still work.
