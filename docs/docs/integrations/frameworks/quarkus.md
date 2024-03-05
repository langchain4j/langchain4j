---
sidebar_position: 3
---

# Quarkus

The Quarkus [LangChain4j extension](https://quarkus.io/extensions/io.quarkiverse.langchain4j/quarkus-langchain4j-core/) seamlessly integrates with the Quarkus programming model and existing Quarkus runtime components.

The extension offers the following advantages over using the vanilla LangChain4j library in Quarkus:

- Integration with the Quarkus programming model
    - A new `@RegisterAiService` annotation for declarative AI services
    - Injectable CDI beans for the LangChain4j models
- Ability to compile to a GraalVM native binary
-Standard configuration properties for configuring models
- Built-in observability (metrics, tracing, and auditing)
- Build-time wiring. Doing more at build-time reduces the footprint of the LangChain4j library and enables build-time usability hints.


## Dev UI

In Dev mode, the quarkus-langchain4j project provides several pages in the Dev UI to facilitate LangChain4j development:

- AI Services page: provides a table of all AI Services detected in the application along with a list of tools that they are declared to use.
- Embeddings store access: Allows embeddings to be added to the embeddings store and searched.
- Tools page: provides a list of tools detected in the application.
- Chat page: allows you to manually hold a conversation with a chat model. This page is only available if the application contains a chat model.
- Images page: allows you to test the outputs of image models and tune its parameters (for models which support it).
- Moderation page: allows you to test the outputs of moderation models - you submit a prompt and receive a list of scores for each appropriateness category (for models which support it).


For more detailed explanation of the extension features, see the [Quarkus documentation](https://docs.quarkiverse.io/quarkus-langchain4j/dev/) for the langchain4j extension. 