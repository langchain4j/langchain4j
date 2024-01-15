---
sidebar_position: 1
title: Introduction
---

# Introduction

Welcome to the **LangChain4j** documentation website!

[![](https://img.shields.io/twitter/follow/langchain4j)](https://twitter.com/intent/follow?screen_name=langchain4j)
[![](https://dcbadge.vercel.app/api/server/JzTFvyjG6R?compact=true&style=flat)](https://discord.gg/JzTFvyjG6R)

LangChain4j simplifies the **integration of AI/LLM capabilities into your Java application**. 
It is based on the popular Python library LangChain.

### Supercharge your Java application with the power of LLMs

[![](/img/langchain4j-components.png)](/docs/intro)

LangChain4j allows for easy interaction with AI/LLMs thanks to:

- **A simple and coherent layer of abstractions**, designed to ensure that your code does not depend on concrete
  implementations such as LLM providers, embedding store providers, etc. This allows for easy swapping of components.
- **Numerous implementations of the above-mentioned abstractions**, providing you with a variety of LLMs and embedding
  stores to choose from.
- **Range of in-demand features on top of LLMs, such as:**
    - The capability to **ingest your own data** (documentation, codebase, etc.), allowing the LLM to act and respond
      based on your data.
    - **Autonomous agents** for delegating tasks (defined on the fly) to the LLM, which will strive to complete them.
    - **Prompt templates** to help you achieve the highest possible quality of LLM responses.
    - **Memory** to provide context to the LLM for your current and past conversations.
    - **Structured outputs** for receiving responses from the LLM with a desired structure as Java POJOs.
    - **"AI Services"** for declaratively defining complex AI behavior behind a simple API.
    - **Chains** to reduce the need for extensive boilerplate code in common use-cases.
    - **Auto-moderation** to ensure that all inputs and outputs to/from the LLM are not harmful.

### Tutorials (User Guide)
Discover inspiring [use cases](/docs/tutorials#need-inspiration) or follow our step-by-step introduction to LangChain4j features under [Tutorials](/docs/category/tutorials).

You will get a tour of all LangChain4j functionality in steps of increasing complexity. All steps are demonstrated with complete code examples and code explanation.

### Integrations and Models
LangChain4j offers ready-to-use integrations with models of OpenAI, HuggingFace, Google, Azure, and many more. 
It has document loaders for all common document types, and integrations with plenty of embedding models and embedding stores, to facilitate retrieval-augmented generation and AI-powered classification.
All integrations are listed [here](/docs/category/integrations).

### Code examples

You can browse through code examples in the `langchain4j-examples` repo:

- [Examples in plain Java](https://github.com/langchain4j/langchain4j-examples/tree/main/other-examples/src/main/java)
- [Example with Spring Boot](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/test/java/dev/example/CustomerSupportApplicationTest.java)

Quarkus specific examples (leveraging the [quarkus-langchain4j](https://github.com/quarkiverse/quarkus-langchain4j)
dependency which builds on this project) can be
found [here](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/samples)


### Disclaimer

Please note that the library is in active development and:

- Many features are still missing. We are working hard on implementing them ASAP.
- API might change at any moment. At this point, we prioritize good design in the future over backward compatibility
  now. We hope for your understanding.
- We need your input! Please [let us know](https://github.com/langchain4j/langchain4j/issues/new/choose) what features
  you need and your concerns about the current implementation.

### Coming soon:

- Extending "AI Service" features
- Integration with more LLM providers (commercial and free)
- Integrations with more embedding stores (commercial and free)
- Support for more document types
- Long-term memory for chatbots and agents
- Chain-of-Thought and Tree-of-Thought

### Request features

Please [let us know](https://github.com/langchain4j/langchain4j/issues/new/choose) what features you need!

### Contribute

Please help us make this open-source library better by contributing to our [github repo](https://github.com/langchain4j/langchain4j).

