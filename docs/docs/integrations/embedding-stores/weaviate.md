---
sidebar_position: 26
---

# Weaviate

https://weaviate.io/

## Maven Dependency

```xml

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-weaviate</artifactId>
    <version>1.0.1-beta6</version>
</dependency>
```

## APIs

- `WeaviateEmbeddingStore`

## Usage

|      Parameter      | Description                                                                                                                                                                                                      | Required/Optional               |
|:-------------------:|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|
|      `apiKey`       | Your Weaviate API key. Not required for local deployment.                                                                                                                                                        | Optional                        |
|      `scheme`       | The scheme, e.g. "https" of cluster URL. Find it under Details of your Weaviate cluster.                                                                                                                         | Required                        |
|       `host`        | The host, e.g. "langchain4j-4jw7ufd9.weaviate.network" of cluster URL. Find it under Details of your Weaviate cluster.                                                                                           | Required                        |
|       `port`        | The port, e.g. 8080.                                                                                                                                                                                             | Optional                        |
|    `objectClass`    | The object class you want to store, e.g. "MyGreatClass". Must start from an uppercase letter.                                                                                                                    | Optional (default: `Default`)   |
|     `avoidDups`     | If `true` (default), then `WeaviateEmbeddingStore` will generate a hashed ID based on the provided text segment, which avoids duplicated entries in DB. If false, then a random ID will be generated.            | Optional (default: `true`)      |
| `consistencyLevel`  | Consistency level: `ONE`, `QUORUM` (default) or `ALL`. Find more details [here](https://weaviate.io/developers/weaviate/concepts/replication-architecture/consistency#tunable-write-consistency).                | Optional (default: `QUORUM`)    |
| `useGrpcForInserts` | Use GRPC instead of HTTP for batch inserts only. You still need HTTP configured for search.                                                                                                                      | Optional                        |
|    `securedGrpc`    | The GRPC connection is secured.                                                                                                                                                                                  | Optional                        |
|     `grpcPort`      | The port, e.g. 50051.                                                                                                                                                                                            | Optional                        |
|   `textFieldName`   | The name of the field that contains the text of a `TextSegment`.                                                                                                                                                 | Optional (default: `text`)      |
| `metadataFieldName` | The name of the field where `Metadata` entries are stored. If set to an empty string (`""`), `Metadata` entries will be stored in the root object. It is recommended to use `metadataKeys` if using root object. | Optional (default: `_metadata`) |
|   `metadataKeys`    | Metadata keys that should be persisted.                                                                                                                                                                          | Optional                        |

## Examples

- [WeaviateEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/weaviate-example/src/main/java/WeaviateEmbeddingStoreExample.java)
