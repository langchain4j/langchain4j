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
    <version>1.24.0-beta34</version>
</dependency>
```


## APIs

- `InfinispanEmbeddingStore`
- `InfinispanChatMemoryStore`
- `InfinispanContentRetriever`

Both `InfinispanEmbeddingStore` and `InfinispanChatMemoryStore` implement the non-blocking methods of their
interfaces: `searchAsync` on `InfinispanEmbeddingStore`, and `getMessagesAsync`, `updateMessagesAsync` and
`deleteMessagesAsync` on `InfinispanChatMemoryStore`. They use the asynchronous API of the Infinispan Hot Rod
client, so you can use them in asynchronous and reactive AI Services and RAG without blocking a thread.
The returned futures complete on the I/O threads of the Hot Rod client, so do not block in code that you chain to them.
See [Non-blocking and Reactive](/tutorials/non-blocking).

### InfinispanChatMemoryStore

A `ChatMemoryStore` backed by an Infinispan cache. Messages are serialized to JSON
using `ChatMessageSerializer`, supporting all message types including multimodal
content (images, audio, video, PDF), tool calls, and provider-specific attributes.

```java
// Using a ConfigurationBuilder
InfinispanChatMemoryStore store = InfinispanChatMemoryStore.builder()
    .infinispanConfigBuilder(builder)
    .build();
```

When using a pre-configured `RemoteCacheManager` (e.g. in Quarkus):

```java
InfinispanChatMemoryStore store = InfinispanChatMemoryStore.builder()
    .remoteCacheManager(remoteCacheManager)
    .build();
```

### InfinispanContentRetriever

A `ContentRetriever` that uses Infinispan's full-text search (Ickle queries powered by Hibernate Search / Lucene)
to retrieve relevant content without requiring embeddings. This is useful for RAG pipelines where you want
keyword-based retrieval instead of (or in addition to) vector similarity search.

When full-text search is enabled, the `text` field is indexed with the `standard` analyzer, which tokenizes
text and applies lowercase normalization.

```java
InfinispanContentRetriever retriever = InfinispanContentRetriever.builder()
    .cacheName("my-cache")
    .dimension(384)
    .infinispanConfigBuilder(builder)
    .maxResults(5)
    .build();

List<Content> results = retriever.retrieve(new Query("search terms"));
```

The retriever also supports non-blocking retrieval via `retrieveAsync`.

#### Advanced: custom text field annotation

By default, `InfinispanContentRetriever` uses `@Text(projectable=true, analyzer="standard")` on the `text`
field, while `InfinispanEmbeddingStore` uses `@Basic(projectable=true)` (no full-text analysis needed for
vector search). These are Infinispan Protobuf schema annotations that control how the text field is indexed.

If you need a different analyzer (e.g., for a specific language or custom tokenization), you can override
the annotation:

```java
InfinispanContentRetriever retriever = InfinispanContentRetriever.builder()
    .cacheName("my-cache")
    .dimension(384)
    .infinispanConfigBuilder(builder)
    .textFieldAnnotation("@Text(projectable=true, analyzer=\"keyword\")")
    .build();
```

See the [Infinispan indexing documentation](https://infinispan.org/docs/stable/titles/query/query.html)
for the full list of available analyzers and annotation options.

## Examples

- [InfinispanEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/infinispan-example/src/main/java/InfinispanEmbeddingStoreExample.java)
