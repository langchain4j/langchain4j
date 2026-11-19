---
sidebar_position: 27
---

# Weaviate

https://weaviate.io/

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-weaviate</artifactId>
    <version>1.24.0-beta34</version>
</dependency>
```

## APIs

- `WeaviateEmbeddingStore`

## Requirements

Starting from `1.25.0-beta35`, `langchain4j-weaviate` is built on the Weaviate Java client v6
(`io.weaviate:client6`) and no longer uses the GraphQL API of Weaviate. This comes with two requirements:

- Weaviate **1.32 or newer**.
- The **gRPC port** of Weaviate (50051 by default) must be reachable, in addition to the HTTP port.
  Inserts, searches and most deletes go over gRPC. Earlier versions needed only HTTP.

Other changes in `1.25.0-beta35`:

- `useGrpcForInserts` and `securedGrpc` are deprecated and ignored.
- Adding embeddings fails with an exception when Weaviate rejects an object. Earlier versions ignored such errors.
- `removeAll()` and `removeAll(Filter)` cover the whole collection. Earlier versions only processed
  the first page of objects (`QUERY_DEFAULTS_LIMIT`).
- Removing embeddings fails with an exception when Weaviate reports that some of them were not removed.
- `WeaviateEmbeddingStore` connects to Weaviate when it is built, and `build()` fails when Weaviate
  is not reachable or is older than 1.32. Earlier versions connected on first use.
- Errors reported by Weaviate are thrown as exceptions of the Weaviate client (`WeaviateException`).
- `WeaviateEmbeddingStore` is `AutoCloseable`. Close it when it is no longer needed to release its connections.
- Every request carries the `X-Weaviate-Client-Integration: langchain4j/<version>` header, which identifies the integration in Weaviate's usage telemetry.
- Embeddings are added in batches, see [Batching](#batching).

Existing collections keep working and do not need to be re-created.

## Usage

|      Parameter      | Description                                                                                                                                                                                                      | Required/Optional               |
|:-------------------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|
|      `apiKey`       | Your Weaviate API key. Not required for local deployment.                                                                                                                                                        | Optional                        |
|      `scheme`       | The scheme, e.g. "https" of cluster URL. Find it under Details of your Weaviate cluster. It applies to both HTTP and gRPC.                                                                                       | Required                        |
|       `host`        | The host, e.g. "langchain4j-4jw7ufd9.weaviate.network" of cluster URL. It may carry the HTTP port, e.g. "localhost:8080".                                                                                        | Required                        |
|       `port`        | The HTTP port, e.g. 8080. The default is 443 for the "https" scheme and 80 otherwise.                                                                                                                            | Optional                        |
|    `objectClass`    | The object class you want to store, e.g. "MyGreatClass". Must start from an uppercase letter.                                                                                                                    | Optional (default: `Default`)   |
|     `avoidDups`     | If `true` (default), then `WeaviateEmbeddingStore` will generate a hashed ID based on the provided text segment, which avoids duplicated entries in DB. If false, then a random ID will be generated.            | Optional (default: `true`)      |
| `consistencyLevel`  | Consistency level: `ONE`, `QUORUM` (default) or `ALL`. Find more details [here](https://weaviate.io/developers/weaviate/concepts/replication-architecture/consistency#tunable-write-consistency).                | Optional (default: `QUORUM`)    |
| `useGrpcForInserts` | Deprecated and ignored: gRPC is always used, for inserts, searches and deletes.                                                                                                                                  | Optional                        |
|    `securedGrpc`    | Deprecated and ignored: the gRPC connection is secured when `scheme` is "https".                                                                                                                                 | Optional                        |
|     `grpcPort`      | The gRPC port. For a Weaviate Cloud host with an API key and no `grpcPort`, the gRPC endpoint of the cluster is used.                                                                                                   | Optional (default: `50051`)     |
|   `textFieldName`   | The name of the field that contains the text of a `TextSegment`.                                                                                                                                                 | Optional (default: `text`)      |
| `metadataFieldName` | The name of the field where `Metadata` entries are stored. If set to an empty string (`""`), `Metadata` entries will be stored in the root object. It is recommended to use `metadataKeys` if using root object. | Optional (default: `_metadata`) |
|   `metadataKeys`    | Metadata keys that should be persisted.                                                                                                                                                                          | Optional                        |
|     `batchSize`     | The maximum number of objects sent to Weaviate in one request when adding embeddings. See [Batching](#batching).                                                                                                 | Optional (default: `100`)       |
| `serverSideBatching` | If `true`, embeddings are added with the server-side batching of Weaviate. Requires Weaviate 1.36 or newer. See [Batching](#batching).                                                                           | Optional (default: `false`)     |
|  `batchMaxRetries`  | How many times an object is sent again when Weaviate fails to import it. Applies only with `serverSideBatching`.                                                                                                 | Optional                        |

## Batching

`addAll` sends the embeddings to Weaviate in batches. There are two ways to do it:

- **Fixed-size batches** (default): one request per `batchSize` objects (100 by default).
  Lower `batchSize` for very large objects, and raise it to reduce the number of requests.
- **Server-side batching**: the objects are streamed to Weaviate, which controls the pace of the import
  depending on its load. Requires Weaviate 1.36 or newer. `batchSize` then sets the number of objects
  per message of the stream, and `batchMaxRetries` how many times a failed object is sent again.

```java
EmbeddingStore<TextSegment> embeddingStore = WeaviateEmbeddingStore.builder()
        .scheme("http")
        .host("localhost")
        .port(8080)
        .grpcPort(50051)
        .objectClass("MyGreatClass")
        .serverSideBatching(true)
        .build();
```

In both cases `addAll` returns when all the objects are stored, and fails with an exception
when Weaviate rejects any of them. Objects of batches that were already stored are not rolled back.

## Weaviate 1.39 and Newer

Starting from version 1.39, Weaviate stores the embedding of an object in a *named vector*
(called `default`) when it creates a collection automatically. Older versions of Weaviate
used a single unnamed vector instead.

`langchain4j-weaviate` `1.24.0-beta34` and earlier read only the single unnamed vector.
When such a version is used with a collection created by Weaviate 1.39 or newer,
`EmbeddingMatch.embedding()` is empty for every search result. The score, the ID,
the `TextSegment` and its `Metadata` are returned correctly, so this is only relevant
if your application uses the embedding of a match.

This is fixed in `1.24.0-beta34`, which reads both layouts: embeddings that were stored
by an earlier version are returned as well, and no change is needed.

With `1.24.0-beta34` and earlier, create the collection yourself, before using
`WeaviateEmbeddingStore`, and configure it without a named vector:

```java
WeaviateClient client = new WeaviateClient(new Config("https", "my-cluster.weaviate.network"));

client.schema().classCreator()
        .withClass(WeaviateClass.builder()
                .className("MyGreatClass")
                .vectorizer("none")
                .build())
        .run();
```

The same can be done with a plain HTTP request:

```shell
curl -X POST https://my-cluster.weaviate.network/v1/schema \
  -H 'Content-Type: application/json' \
  -d '{"class": "MyGreatClass", "vectorizer": "none"}'
```

Collections created by Weaviate 1.38 or older are not affected.

## Examples

- [WeaviateEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/weaviate-example/src/main/java/WeaviateEmbeddingStoreExample.java)
