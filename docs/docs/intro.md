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

### Features
- Integration with more than 10 managed and self-hosted language models (LLMs) for chat and completion
- Prompt templates
- Support for texts and images as inputs (multimodality)
- Streaming of responses from language models
- Tools for tokenization and estimation of token counts
- Output parsers for common Java types (e.g., `List`, `LocalDate`, etc.) and custom POJOs
- Integration with over three managed and self-hosted image generation models
- Integration with more than 10 managed and self-hosted embedding models
- Integration with more than 15 managed and self-hosted embedding stores
- Integration with one moderation model: OpenAI
- Integration with one scoring (re-ranking) model: Cohere (with more expected to come)
- Tools (function calling)
- Dynamic Tools (execution of dynamically generated LLM code)
- "Lite" agents (OpenAI functions)
- AI Services
- Chains (legacy)
- Implementation of persistent and in-memory chat memory algorithms: message window and token window
- Text classification
- RAG (Retrieval-Augmented-Generation):
  - Ingestion:
    - Importing various types of documents (TXT, PDFs, DOC, PPT, XLS etc.) from multiple sources (file system, URL, GitHub, Azure Blob Storage, Amazon S3, etc.)
    - Splitting documents into smaller segments using multiple splitting algorithms
    - Post-processing of documents and segments
    - Embedding segments using embedding models
    - Storing embeddings in embedding (vector) store
  - Retrieval (simple and advanced):
    - Transformation of queries (expansion, compression)
    - Routing of queries
    - Retrieving from vector store and/or any custom sources
    - Re-ranking
    - Reciprocal Rank Fusion
    - Customization of each step in the RAG flow

### 2 levels of abstraction
LangChain4j operates on two levels of abstraction:
- Low level. At this level, you have the most freedom and access to all the low-level components such as
`ChatLanguageModel`, `UserMessage`, `AiMessage`, `EmbeddingStore`, `Embedding`, etc.
These are the "primitives" of your LLM-powered application.
You have complete control over how to combine them, but you will need to write more glue code.
- High level. At this level, you interact with LLMs using high-level APIs like `AiServices` and `Chain`s,
which hides all the complexity and boilerplate from you.
You still have the flexibility to adjust and fine-tune the behavior, but it is done in a declarative manner.

[![](/img/langchain4j-components.png)](/intro)

### Library Structure
LangChain4j features a modular design, comprising:
- The `langchain4j-core` module, which defines core abstractions (such as `ChatLanguageModel` and `EmbeddingStore`) and their APIs.
- The main `langchain4j` module, containing useful tools like `ChatMemory`, `OutputParser` as well as a high-level features like `AiServices`.
- A wide array of `langchain4j-{integration}` modules, each providing integration with various LLM providers and embedding stores into LangChain4j.
  You can use the `langchain4j-{integration}` modules independently. For additional features, simply import the main `langchain4j` dependency.

### Tutorials (User Guide)
Discover inspiring [use cases](/tutorials/#or-consider-some-of-the-use-cases) or follow our step-by-step introduction to LangChain4j features under [Tutorials](/category/tutorials).

You will get a tour of all LangChain4j functionality in steps of increasing complexity. All steps are demonstrated with complete code examples and code explanation.

### Integrations and Models
LangChain4j offers ready-to-use integrations with models of OpenAI, HuggingFace, Google, Azure, and many more. 
It has document loaders for all common document types, and integrations with plenty of embedding models and embedding stores, to facilitate retrieval-augmented generation and AI-powered classification.
All integrations are listed [here](/category/integrations).

### Code Examples

You can browse through code examples in the `langchain4j-examples` repo:

- [Examples in plain Java](https://github.com/langchain4j/langchain4j-examples/tree/main/other-examples/src/main/java)
- [Example with Spring Boot](https://github.com/langchain4j/langchain4j-examples/blob/main/spring-boot-example/src/test/java/dev/example/CustomerSupportApplicationTest.java)

Quarkus specific examples (leveraging the [quarkus-langchain4j](https://github.com/quarkiverse/quarkus-langchain4j)
dependency which builds on this project) can be
found [here](https://github.com/quarkiverse/quarkus-langchain4j/tree/main/samples)

### Useful Materials
[Useful Materials](https://docs.langchain4j.dev/useful-materials)

### Disclaimer

Please note that the library is in active development and:

- Some features are still missing. We are working hard on implementing them ASAP.
- API might change at any moment. At this point, we prioritize good design in the future over backward compatibility
  now. We hope for your understanding.
- We need your input! Please [let us know](https://github.com/langchain4j/langchain4j/issues/new/choose) what features
  you need and your concerns about the current implementation.

### Coming soon

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

