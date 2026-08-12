---
sidebar_position: 34
---

# Hazelcast

[Hazelcast](https://hazelcast.com/) is a distributed in-memory data grid and computing platform.
LangChain4j integrates with Hazelcast through two modules, split so the open-source path
stays free of any Enterprise/licensing requirement:

- **`langchain4j-community-hazelcast`** (open source) — provides `HazelcastChatMemoryStore`, a
  `ChatMemoryStore` backed by a Hazelcast `IMap`. Runs against the open-source Community Edition
  with no license.
- **`langchain4j-community-hazelcast-enterprise`** (requires Hazelcast Enterprise) — provides
  `HazelcastEmbeddingStore` (vector search via `VectorCollection`) and
  `HazelcastCPMapChatMemoryStore` (a strongly-consistent, CP-Subsystem-backed chat memory store).
  This module re-exports `langchain4j-community-hazelcast`, so Enterprise consumers also get the
  `IMap`-based store from the single dependency.

## Maven Dependency

### Open source (Chat Memory Store)

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-hazelcast</artifactId>
    <version>${latest version here}</version>
</dependency>
```

The Hazelcast dependency is `provided`, so add the edition you run against. By default this is the
open-source Community Edition:

```xml
<dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast</artifactId>
    <version>5.7.0</version>
</dependency>
```

### Hazelcast Enterprise (Embedding Store + CP chat memory)

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-hazelcast-enterprise</artifactId>
    <version>${latest version here}</version>
</dependency>
```

Or use the BOM to manage versions consistently:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-community-bom</artifactId>
            <version>${latest version here}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

:::note
`HazelcastEmbeddingStore` and `HazelcastCPMapChatMemoryStore` require **Hazelcast Enterprise**.
`com.hazelcast:hazelcast-enterprise` is not on Maven Central — it is pulled in transitively by
`langchain4j-community-hazelcast-enterprise`, but you must add the Hazelcast release repository and a
valid Enterprise license key:

```xml
<repositories>
    <repository>
        <id>hazelcast-release</id>
        <name>Hazelcast Release Repository</name>
        <url>https://repository.hazelcast.com/release/</url>
    </repository>
</repositories>
```

Provide the license key via `config.setLicenseKey(...)` or the `HZ_LICENSEKEY` environment variable.
`HazelcastCPMapChatMemoryStore` additionally requires the CP Subsystem to be enabled; it fails fast
otherwise.
:::

## Chat Memory Store

`HazelcastChatMemoryStore` (open source) stores each chat memory as a JSON-serialised list of
`ChatMessage`s in a Hazelcast `IMap`. The `HazelcastInstance` you supply may be an embedded member or
a thin client — the builder does not distinguish.

```java
// Embedded member
HazelcastInstance hz = Hazelcast.newHazelcastInstance(new Config());

ChatMemoryStore store = HazelcastChatMemoryStore.builder()
        .hazelcastInstance(hz)
        .name("chatMemory") // optional, defaults to "chatMemory"
        .build();
```

```java
// Client connecting to an external cluster
ClientConfig clientConfig = new ClientConfig();
clientConfig.getNetworkConfig().addAddress("hazelcast-host:5701");
HazelcastInstance hzClient = HazelcastClient.newHazelcastClient(clientConfig);

ChatMemoryStore store = HazelcastChatMemoryStore.builder()
        .hazelcastInstance(hzClient)
        .build();
```

You can also wrap a pre-configured `IMap` directly with
`HazelcastChatMemoryStore.create(IMap<String, String>)`.

### Strongly-consistent variant (Enterprise)

`HazelcastCPMapChatMemoryStore` (Enterprise) is an alternative backed by a `CPMap` in the
[CP Subsystem](https://docs.hazelcast.com/hazelcast/latest/cp-subsystem/cp-subsystem). It is
**linearizable** (strongly consistent, Raft-backed), avoiding lost updates when the same `memoryId`
is updated concurrently. The CP Subsystem must be enabled on the instance.

```java
Config config = new Config();
config.getCPSubsystemConfig().setCPMemberCount(3); // CP Subsystem must be enabled
HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);

ChatMemoryStore store = HazelcastCPMapChatMemoryStore.builder()
        .hazelcastInstance(hz)
        .name("chatMemory")
        .build();
```

Trade-offs versus the `IMap` store: a `CPMap` is **not partitioned** (it must fit within each CP
member's RAM, default 100 MB total) and has **no TTL/eviction**. It suits many small conversations;
prefer the `IMap`-based store for unbounded history across a very large user population.

## Embedding Store

`HazelcastEmbeddingStore` (Enterprise) is backed by a Hazelcast `VectorCollection`. The vector index
dimension must match the embedding model in use; the metric defaults to `COSINE`.

```java
HazelcastInstance hz = Hazelcast.newHazelcastInstance(new Config());

EmbeddingStore<TextSegment> store = HazelcastEmbeddingStore.builder()
        .hazelcastInstance(hz)
        .collectionName("embeddings")   // optional, defaults to "embeddings"
        .dimension(384)                 // required, must match the embedding model
        .metric(Metric.COSINE)          // optional, defaults to COSINE
        .build();
```

You can also wrap a pre-configured `VectorCollection` with
`HazelcastEmbeddingStore.create(VectorCollection<String, TextSegmentDocument>)`. The relevance score
returned by `search(...)` is Hazelcast's already-normalised COSINE score, used directly.

### Limitations

- `removeAll(Filter)` is **not supported** — `VectorCollection` has no server-side predicate delete;
  it throws `UnsupportedFeatureException`. Removal by id, `removeAll(Collection<String>)` and
  `removeAll()` are supported.
- Metadata filtering during search is **not** performed server-side. If `EmbeddingSearchRequest`
  carries a filter, it is applied **client-side after retrieval** (a warning is logged), which may
  return fewer than `maxResults` matches.

## APIs

- `HazelcastChatMemoryStore` — `IMap`-based (AP), open source
- `HazelcastCPMapChatMemoryStore` — `CPMap`-based (CP, linearizable), Enterprise
- `HazelcastEmbeddingStore` — `VectorCollection`-based vector store, Enterprise

## Examples

- [HazelcastChatMemoryStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/hazelcast-example/src/main/java/HazelcastChatMemoryStoreExample.java)
- [HazelcastCPMapChatMemoryStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/hazelcast-example/src/main/java/HazelcastCPMapChatMemoryStoreExample.java)
- [HazelcastEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/hazelcast-example/src/main/java/HazelcastEmbeddingStoreExample.java)
