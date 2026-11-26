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
to retrieve relevant content. The query does not need an embedding model. This is useful for RAG pipelines where you
want keyword-based retrieval instead of (or in addition to) vector similarity search.

The retriever is built from an existing `InfinispanEmbeddingStore`, reusing its remote cache and configuration.
The store must have full-text indexing enabled on the text field.

Multi-word queries are split into individual terms and matched with OR logic, ranked by score. A query
like "What is Infinispan?" matches any document containing "what", "is", or "infinispan".

```java
// 1. Build the store with full-text annotation enabled
InfinispanEmbeddingStore store = InfinispanEmbeddingStore.builder()
    .cacheName("my-cache")
    .dimension(384)
    .textFieldAnnotation(InfinispanStoreConfiguration.TEXT_FIELD_ANNOTATION_FULLTEXT)
    .infinispanConfigBuilder(builder)
    .build();

// 2. Build the retriever from the store
InfinispanContentRetriever retriever = InfinispanContentRetriever.builder()
    .embeddingStore(store)
    .maxResults(5)
    .build();

List<Content> results = retriever.retrieve(new Query("search terms"));
```

Each returned `Content` has the full-text score (`ContentMetadata.SCORE`) and the id of the stored entry
(`ContentMetadata.EMBEDDING_ID`) in its metadata. The retriever also supports non-blocking retrieval via
`retrieveAsync`.

If the cache already holds data that was stored with the default text field annotation, switching the store to
full-text is not enough: the existing index does not change by itself, and the retriever fails with
`ISPN028521: Full-text queries cannot be applied to property 'text'`. Update the index schema and reindex the
cache once:

```java
store.remoteCache().getRemoteCacheManager().administration().updateIndexSchema("my-cache");
store.remoteCache().getRemoteCacheManager().administration().reindexCache("my-cache");
```

#### Custom text field annotation

By default, `InfinispanEmbeddingStore` uses `@Basic(projectable=true)` on the text field (no full-text
analysis needed for vector search). To enable full-text search, set
`textFieldAnnotation(InfinispanStoreConfiguration.TEXT_FIELD_ANNOTATION_FULLTEXT)` which uses the
`standard` analyzer (tokenizes text and applies lowercase normalization).

For a different analyzer, pass a custom annotation to the store builder. For example, the `stemmer` analyzer also
matches other forms of a word ("run" finds "running") and ignores common English words such as "what" and "is":

```java
InfinispanEmbeddingStore store = InfinispanEmbeddingStore.builder()
    .cacheName("my-cache")
    .dimension(384)
    .textFieldAnnotation("@Text(projectable=true, analyzer=\"stemmer\")")
    .infinispanConfigBuilder(builder)
    .build();
```

See the [Infinispan indexing documentation](https://infinispan.org/docs/stable/titles/query/query.html)
for the full list of available analyzers and annotation options.

## Examples

- [InfinispanEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/infinispan-example/src/main/java/InfinispanEmbeddingStoreExample.java)
