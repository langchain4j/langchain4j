---
sidebar_position: 1
title: Introduction
---

# Introduction

Welcome!

The goal of LangChain4j is to simplify integrating AI/LLM capabilities into Java applications.

Here's how:
1. **Unified APIs:**
LLM providers (like OpenAI or Google Vertex AI) and embedding (vector) stores (such as Pinecone or Vespa)
use proprietary APIs. LangChain4j offers a unified API to avoid the need for learning and implementing specific APIs for each of them.
To experiment with a different LLM or embedding store, you can easily switch between them without the need to rewrite your code.
LangChain4j currently supports over 10 popular LLM providers and more than 15 embedding stores.
Think of it as a Hibernate, but for LLMs and embedding stores.
2. **Comprehensive Toolbox:**
During the past year, the community has been building numerous LLM-powered applications,
identifying common patterns, abstractions, and techniques. LangChain4j has refined these into practical code.
Our toolbox includes tools ranging from low-level prompt templating, memory management, and output parsing
to high-level patterns like Agents and RAGs.
For each pattern and abstraction, we provide an interface along with multiple ready-to-use implementations based on proven techniques.
Whether you're building a chatbot or developing a RAG with a complete pipeline from data ingestion to retrieval,
LangChain4j offers a wide variety of options.
3. **Numerous Examples:**
These [examples](https://github.com/langchain4j/langchain4j-examples) showcase how to begin creating various LLM-powered applications,
providing inspiration and enabling you to start building quickly.

LangChain4j began development in early 2023 amid the ChatGPT hype.
We noticed a lack of Java counterparts to the numerous Python and JavaScript LLM libraries and frameworks,
and we had to fix that!
Although "LangChain" is in our name, the project is a fusion of ideas and concepts from LangChain, Haystack,
LlamaIndex, and the broader community, spiced up with a touch of our own innovation.

We actively monitor community developments, aiming to quickly incorporate new techniques and integrations,
ensuring you stay up-to-date.
The library is under active development. While some features from the Python version of LangChain
are still being worked on, the core functionality is in place, allowing you to start building LLM-powered apps now!

For easier integration, LangChain4j also includes integration with
Quarkus ([extension](https://quarkus.io/extensions/io.quarkiverse.langchain4j/quarkus-langchain4j-core))
and Spring Boot ([starters](https://github.com/langchain4j/langchain4j-spring)).

### LangChain4j Structure
LangChain4j features a modular design, comprising:
- The `langchain4j-core` module, which defines core abstractions (such as `ChatLanguageModel` and `EmbeddingStore`) and their APIs.
- The main `langchain4j` module, containing useful tools like `ChatMemory`, `OutputParser` as well as a high-level features like `AiServices`.
- A wide array of `langchain4j-xyz` modules, each providing integration with various LLM providers and embedding stores into LangChain4j.
  You can use the `langchain4j-xyz` modules independently. For additional features, simply import the main `langchain4j` dependency.

[![](/img/langchain4j-components.png)](/docs/intro)

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

