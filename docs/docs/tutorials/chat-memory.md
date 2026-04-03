---
sidebar_position: 3
---

# Chat Memory

Maintaining and managing `ChatMessage`s manually is cumbersome.
Therefore, LangChain4j offers a `ChatMemory` abstraction along with multiple out-of-the-box implementations.

`ChatMemory` can be used as a standalone low-level component,
or as a part of a high-level component like [AI Services](/tutorials/ai-services).

`ChatMemory` acts as a container for `ChatMessage`s (backed by a `List`), with additional features like:
- Eviction policy
- Persistence
- Special treatment of `SystemMessage`
- Special treatment of [tool](/tutorials/tools) messages

## Memory vs History

Please note that "memory" and "history" are similar, yet distinct concepts.
- History keeps **all** messages between the user and AI **intact**. History is what the user sees in the UI. It represents what was actually said.
- Memory keeps **some information**, which is presented to the LLM to make it behave as if it "remembers" the conversation.
Memory is quite different from history. Depending on the memory algorithm used, it can modify history in various ways:
evict some messages, summarize multiple messages, summarize separate messages, remove unimportant details from messages,
inject extra information (e.g., for RAG) or instructions (e.g., for structured outputs) into messages, and so on.

LangChain4j currently offers only "memory", not "history". If you need to keep an entire history, please do so manually.

## Eviction policy

An eviction policy is necessary for several reasons:
- To fit within the LLM's context window. There is a cap on the number of tokens LLM can process at once.
At some point, conversation might exceed this limit. In such cases, some message(s) should be evicted.
Usually, the oldest message(s) are evicted, but more sophisticated algorithms can be implemented if needed.
- To control the cost. Each token has a cost, making each call to the LLM progressively more expensive.
Evicting unnecessary messages reduces the cost.
- To control the latency. The more tokens are sent to the LLM, the more time it takes to process them.

Currently, LangChain4j offers 2 out-of-the-box implementations:
- The simpler one, `MessageWindowChatMemory`, functions as a sliding window,
  retaining the `N` most recent messages and evicting older ones that no longer fit.
  However, because each message can contain a varying number of tokens,
`MessageWindowChatMemory` is mostly useful for fast prototyping.
- A more sophisticated option is the `TokenWindowChatMemory`,
  which also operates as a sliding window but focuses on keeping the `N` most recent **tokens**,
  evicting older messages as needed.
  Messages are indivisible. If a message doesn't fit, it is evicted completely.
  `TokenWindowChatMemory` requires a `TokenCountEstimator` to count the tokens in each `ChatMessage`.

## Persistence

By default, `ChatMemory` implementations store `ChatMessage`s in memory.

If persistence is required, a custom `ChatMemoryStore` can be implemented
to store `ChatMessage`s in any persistent store of your choice:
```java
class PersistentChatMemoryStore implements ChatMemoryStore {

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
          // TODO: Implement getting all messages from the persistent store by memory ID.
          // ChatMessageDeserializer.messageFromJson(String) and 
          // ChatMessageDeserializer.messagesFromJson(String) helper methods can be used to
          // easily deserialize chat messages from JSON.
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            // TODO: Implement updating all messages in the persistent store by memory ID.
            // ChatMessageSerializer.messageToJson(ChatMessage) and 
            // ChatMessageSerializer.messagesToJson(List<ChatMessage>) helper methods can be used to
            // easily serialize chat messages into JSON.
        }

        @Override
        public void deleteMessages(Object memoryId) {
          // TODO: Implement deleting all messages in the persistent store by memory ID.
        }
    }

ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .id("12345")
        .maxMessages(10)
        .chatMemoryStore(new PersistentChatMemoryStore())
        .build();
```

The `updateMessages()` method is called every time a new `ChatMessage` is added to the `ChatMemory`.
This usually happens twice during each interaction with the LLM:
once when a new `UserMessage` is added and again when a new `AiMessage` is added.
The `updateMessages()` method is expected to update all messages associated with the given memory ID.
`ChatMessage`s can be stored either separately (e.g., one record/row/object per message) 
or together (e.g., one record/row/object for the entire `ChatMemory`).

:::note
Please note that messages evicted from `ChatMemory` will also be evicted from `ChatMemoryStore`.
When a message is evicted, the `updateMessages()` method is called
with a list of messages that does not include the evicted message.
:::

The `getMessages()` method is called whenever the user of the `ChatMemory` requests all messages.
This typically happens once during each interaction with the LLM.
The value of the `Object memoryId` argument corresponds to the `id` specified
during the creation of the `ChatMemory`.
It can be used to differentiate between multiple users and/or conversations.
The `getMessages()` method is expected to return all messages associated with the given memory ID.

The `deleteMessages()` method is called whenever `ChatMemory.clear()` is called.
If you do not use this functionality, you can leave this method empty.

## Special treatment of `SystemMessage`

`SystemMessage` is a special type of message, so it is treated differently from other message types:
- Once added, a `SystemMessage` is always retained.
- Only one `SystemMessage` can be held at a time.
- If a new `SystemMessage` with the same content is added, it is ignored.
- If a new `SystemMessage` with different content is added, it replaces the previous one.
  By default, the new `SystemMessage` is added to the end of the message list. You can change this by setting
  `alwaysKeepSystemMessageFirst` property when creating `ChatMemory`.

## Special treatment of tool messages

If an `AiMessage` containing `ToolExecutionRequest`s is evicted,
the following orphan `ToolExecutionResultMessage`(s) are also automatically evicted 
to avoid problems with some LLM providers (such as OpenAI)
that prohibit sending orphan `ToolExecutionResultMessage`(s) in the request.

## Examples
- With `AiServices`:
  - [Chat memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithMemoryExample.java)
  - [Separate chat memory for each user](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithMemoryForEachUserExample.java)
  - [Persistent chat memory](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithPersistentMemoryExample.java)
  - [Persistent chat memory for each user](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ServiceWithPersistentMemoryForEachUserExample.java)
- With legacy `Chain`s
  - [Chat memory with ConversationalChain](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatMemoryExamples.java)
  - [Chat memory with ConversationalRetrievalChain](https://github.com/langchain4j/langchain4j-examples/blob/main/other-examples/src/main/java/ChatWithDocumentsExamples.java)

All supported chat memory stores can be found [here](/integrations/chat-memory-stores/).


## Related Tutorials
- [Generative AI Conversations using LangChain4j ChatMemory](https://www.sivalabs.in/generative-ai-conversations-using-langchain4j-chat-memory/) by [Siva](https://www.sivalabs.in/)
