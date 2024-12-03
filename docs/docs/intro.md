---
sidebar_position: 1
title: Introduction
---

# Introduction

Welcome!

The goal of LangChain4j is to simplify integrating LLMs into Java applications.

Here's how:
1. **Unified APIs:**
   LLM providers (like OpenAI or Google Vertex AI) and embedding (vector) stores (such as Pinecone or Milvus)
   use proprietary APIs. LangChain4j offers a unified API to avoid the need for learning and implementing specific APIs for each of them.
   To experiment with different LLMs or embedding stores, you can easily switch between them without the need to rewrite your code.
   LangChain4j currently supports [15+ popular LLM providers](/integrations/language-models/)
   and [20+ embedding stores](/integrations/embedding-stores/).
2. **Comprehensive Toolbox:**
   Since early 2023, the community has been building numerous LLM-powered applications,
   identifying common abstractions, patterns, and techniques. LangChain4j has refined these into a ready to use package.
   Our toolbox includes tools ranging from low-level prompt templating, chat memory management, and function calling
   to high-level patterns like AI Services and RAG.
   For each abstraction, we provide an interface along with multiple ready-to-use implementations based on common techniques.
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
The library is under active development. While some features are still being worked on,
the core functionality is in place, allowing you to start building LLM-powered apps now!

For easier integration, LangChain4j also includes integration with
[Quarkus](/tutorials/quarkus-integration) and [Spring Boot](/tutorials/spring-boot-integration).


## LangChain4j Features
- Integration with [15+ LLM providers](/integrations/language-models)
- Integration with [20+ embedding (vector) stores](/integrations/embedding-stores)
- Integration with [15+ embedding models](/category/embedding-models)
- Integration with [5 image generation models](/category/image-models)
- Integration with [2 scoring (re-ranking) models](/category/scoring-reranking-models)
- Integration with one moderation model (OpenAI)
- Support for texts and images as inputs (multimodality)
- [AI Services](/tutorials/ai-services) (high-level LLM API)
- Prompt templates
- Implementation of persistent and in-memory [chat memory](/tutorials/chat-memory) algorithms: message window and token window
- [Streaming of responses from LLMs](/tutorials/response-streaming)
- Output parsers for common Java types and custom POJOs
- [Tools (function calling)](/tutorials/tools)
- Dynamic Tools (execution of dynamically generated LLM code)
- [RAG (Retrieval-Augmented-Generation)](/tutorials/rag):
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
- Text classification
- Tools for tokenization and estimation of token counts
- [Kotlin Extensions](/tutorials/kotlin): Asynchronous non-blocking handling of chat interactions using Kotlin's coroutine capabilities.

## 2 levels of abstraction
LangChain4j operates on two levels of abstraction:
- Low level. At this level, you have the most freedom and access to all the low-level components such as
[ChatLanguageModel](/tutorials/chat-and-language-models), `UserMessage`, `AiMessage`, `EmbeddingStore`, `Embedding`, etc.
These are the "primitives" of your LLM-powered application.
You have complete control over how to combine them, but you will need to write more glue code.
- High level. At this level, you interact with LLMs using high-level APIs like [AI Services](/tutorials/ai-services),
which hides all the complexity and boilerplate from you.
You still have the flexibility to adjust and fine-tune the behavior, but it is done in a declarative manner.

[![](/img/langchain4j-components.png)](/intro)


## LangChain4j Library Structure
LangChain4j features a modular design, comprising:
- The `langchain4j-core` module, which defines core abstractions (such as `ChatLanguageModel` and `EmbeddingStore`) and their APIs.
- The main `langchain4j` module, containing useful tools like document loaders, [chat memory](/tutorials/chat-memory) implementations as well as a high-level features like [AI Services](/tutorials/ai-services).
- A wide array of `langchain4j-{integration}` modules, each providing integration with various LLM providers and embedding stores into LangChain4j.
  You can use the `langchain4j-{integration}` modules independently. For additional features, simply import the main `langchain4j` dependency.


## LangChain4j Repositories
- [Main repository](https://github.com/langchain4j/langchain4j)
- [Quarkus extension](https://github.com/quarkiverse/quarkus-langchain4j)
- [Spring Boot integration](https://github.com/langchain4j/langchain4j-spring)
- [Community integrations](https://github.com/langchain4j/langchain4j-community)
- [Examples](https://github.com/langchain4j/langchain4j-examples)
- [Community resources](https://github.com/langchain4j/langchain4j-community-resources)
- [In-process embeddings](https://github.com/langchain4j/langchain4j-embeddings)


## Use Cases
You might ask why would I need all of this?
Here are some examples:

- You want to implement a custom AI-powered chatbot that has access to your data and behaves the way you want it:
  - Customer support chatbot that can:
    - politely answer customer questions
    - take /change/cancel orders
  - Educational assistant that can:
    - Teach various subjects
    - Explain unclear parts
    - Assess user's understanding/knowledge
- You want to process a lot of unstructured data (files, web pages, etc) and extract structured information from them.
  For example:
  - extract insights from customer reviews and support chat history
  - extract interesting information from the websites of your competitors
  - extract insights from CVs of job applicants
- You want to generate information, for example:
  - Emails tailored for each of your customers
  - Content for your app/website:
    - Blog posts
    - Stories
- You want to transform information, for example:
  - Summarize
  - Proofread and rewrite
  - Translate
