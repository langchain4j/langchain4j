---
sidebar_position: 3
---

# Chat Memory

Maintaining and managing `ChatMessage`s manually is cumbersome.
Therefore, `ChatMemory` exists.
It essentially acts as a container for `ChatMessage`s (backed by a `List`),
with additional features like persistence (see `ChatMemoryStore`) and mechanisms such as an "eviction policy."
An eviction policy is necessary for two main reasons:
- LLMs have a limited context window, meaning there's a cap on the number of tokens they can process at any given time.
  Eventually, the entire conversation might exceed this limit.
- Each token has a cost, making each call to the LLM progressively more expensive.

Currently, LangChain4j implements two algorithms for eviction policy:
- The simpler one, `MessageWindowChatMemory`, functions as a sliding window,
  retaining the `N` most recent messages and evicting older ones that no longer fit.
  `SystemMessage` is an exception; it is never evicted.
  However, because each message can contain a varying number of tokens, `MessageWindowChatMemory` is mostly useful for fast prototyping.
- A more sophisticated option is the `TokenWindowChatMemory`,
  which also operates as a sliding window but focuses on keeping the `N` most recent **tokens**,
  evicting older messages as needed.
  It requires a `Tokenizer` to count the tokens in each `ChatMessage`. Like before, `SystemMessage` is always preserved.

You can use `ChatMemory` as a standalone low-level component,
or as a part of high-level components like `AiServices` and `ConversationalChain`.

## Examples
- With `AiServices`:
  - [Chat memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithMemoryExample.java)
  - [Separate chat memory for each user](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithMemoryForEachUserExample.java)
  - [Persistent chat memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithPersistentMemoryExample.java)
  - [Persistent chat memory for each user](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithPersistentMemoryForEachUserExample.java)
- With `Chain`s
  - [Chat memory with ConversationalChain](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatMemoryExamples.java)
  - [Chat memory with ConversationalRetrievalChain](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatWithDocumentsExamples.java)

## Related Tutorials
- [Generative AI Conversations using LangChain4j ChatMemory](https://www.sivalabs.in/generative-ai-conversations-using-langchain4j-chat-memory/) by [Siva](https://www.sivalabs.in/)
