---
sidebar_position: 13
---

# Infinispan

Infinispan is an open-source (Apache 2) in-memory key-value database and cache, can hold nearly any type of data, from plain-text 
to structured objects, is designed for high performance, scalability, and low-latency data access. 
It supports both embedded and client-server modes, allowing applications to 
use it as a local cache or a remote server database. 

Infinispan is built in Java and provides features such as persistence, transactions, 
querying (full-text and vector search included), clustering, and support for Protobuf-based data indexing—making it suitable 
for use cases ranging from simple caching to complex real-time data processing in 
microservices and AI applications.

More in https://infinispan.org/

From 15.2 Infinispan Server and above, metadata filtering is supported.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-infinispan</artifactId>
    <version>1.23.0-beta33</version>
</dependency>
```


## APIs

- `InfinispanEmbeddingStore`
- `InfinispanChatMemoryStore`

### InfinispanChatMemoryStore

A `ChatMemoryStore` backed by Infinispan using ProtoStream marshalling.
Messages are stored as protobuf entities with `ChatMessageType` preserved as a protobuf enum.

By default, the cache is created as a distributed cache with `application/x-protostream` encoding
and no indexing. The protobuf schema includes indexing annotations (`@Indexed`, `@Text`, `@Basic`,
`@Embedded`) that are only activated when the cache is configured with indexing enabled.

```java
// Default usage (no indexing)
InfinispanChatMemoryStore store = InfinispanChatMemoryStore.builder()
    .infinispanConfigBuilder(builder)
    .build();
```

#### Enabling indexing for full-text search

To enable full-text search on chat messages, provide a cache configuration with indexing enabled.
The configuration must declare `dev.langchain4j.LangChainChatMemoryItem` as an indexed entity:

```java
String indexedCacheConfig = "<distributed-cache name=\"chat-memory\">\n"
    + "  <indexing storage=\"local-heap\">\n"
    + "    <indexed-entities>\n"
    + "      <indexed-entity>dev.langchain4j.LangChainChatMemoryItem</indexed-entity>\n"
    + "    </indexed-entities>\n"
    + "  </indexing>\n"
    + "</distributed-cache>";

InfinispanChatMemoryStore store = InfinispanChatMemoryStore.builder()
    .infinispanConfigBuilder(builder)
    .cacheConfig(indexedCacheConfig)
    .build();
```

With indexing enabled, you can run [Ickle queries](https://infinispan.org/docs/stable/titles/query/query.html) directly
on the underlying Infinispan cache. The `text` and `thinking` fields use `@Text` for full-text search,
while `memoryId`, `type`, `name`, `toolExecutionId`, and `toolName` use `@Basic` for exact match filtering.
The `messages` field uses `@Embedded(structure=NESTED)` to preserve message-level field correlation in queries.

Example Ickle queries:

```sql
-- Full-text search across all messages
FROM dev.langchain4j.LangChainChatMemoryItem m
WHERE m.messages.text : 'vintage store'

-- Full-text search on USER messages only (using NESTED join)
SELECT m.memoryId FROM dev.langchain4j.LangChainChatMemoryItem m
JOIN m.messages msg WHERE msg.type = 'USER' AND msg.text : 'weather'

-- Fuzzy search (typo-tolerant)
FROM dev.langchain4j.LangChainChatMemoryItem m
WHERE m.messages.text : 'cofee'~2

-- Find conversations that used a specific tool
FROM dev.langchain4j.LangChainChatMemoryItem m
JOIN m.messages msg WHERE msg.toolName = 'get_weather'
```

When using a pre-configured `RemoteCacheManager` (e.g. in Quarkus), the ProtoStream schema
and marshallers must be registered externally:

```java
InfinispanChatMemoryStore store = InfinispanChatMemoryStore.builder()
    .remoteCacheManager(remoteCacheManager)
    .build();
```

## Examples

- [InfinispanEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/infinispan-example/src/main/java/InfinispanEmbeddingStoreExample.java)
