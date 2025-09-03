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
    <version>1.4.0-beta10</version>
</dependency>
```


## APIs

- `InfinispanEmbeddingStore`


## Examples

- [InfinispanEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/infinispan-example/src/main/java/InfinispanEmbeddingStoreExample.java)
