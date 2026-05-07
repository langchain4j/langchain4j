---
sidebar_position: 4
---

# Azure DocumentDB (with MongoDB compatibility)

[Azure DocumentDB (with MongoDB compatibility)](https://learn.microsoft.com/azure/documentdb/) — formerly known as
*Azure Cosmos DB for MongoDB vCore* — is a managed MongoDB‑compatible
service on Azure. The wire protocol, connection strings, drivers, and
APIs are unchanged from the previous name; only the product brand was
renamed. LangChain4j integrates with Azure DocumentDB through the native
MongoDB Java driver and uses the
[`$search` (cosmosSearch)](https://learn.microsoft.com/azure/documentdb/vector-search)
aggregation stage to perform approximate nearest‑neighbor (ANN) vector
search.

You can use Azure DocumentDB with LangChain4j to store embeddings,
create vector search indexes, and build a RAG pipeline.

## Prerequisites

- An Azure DocumentDB cluster. To create one, follow the
  [Quickstart: Create a cluster using the Azure portal](https://learn.microsoft.com/azure/documentdb/quickstart-portal).
- The cluster's MongoDB **connection string**, plus the database and
  collection names you want to use.
- An API key for an embedding provider (for example, OpenAI, Azure
  OpenAI, or Voyage AI). For end‑to‑end RAG you also need a chat model.

## Maven Dependency

Add the integration plus the MongoDB Java sync driver:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-cosmos-mongo-vcore</artifactId>
    <version>1.14.1-beta24</version>
</dependency>
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongodb-driver-sync</artifactId>
    <version>5.4.0</version>
</dependency>
```

We recommend pinning versions through the LangChain4j BOM:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>1.14.1-beta24</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

> **Note**
> The artifact id (`langchain4j-azure-cosmos-mongo-vcore`), the Java
> package (`dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore`),
> and the class name (`AzureCosmosDbMongoVCoreEmbeddingStore`) are
> retained for backward compatibility. Only the product brand has
> changed.

## APIs

- `AzureCosmosDbMongoVCoreEmbeddingStore`

## Quick start

The example below creates an embedding store backed by Azure DocumentDB,
ingests a document, and runs a vector search.

```java
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.azure.cosmos.mongo.vcore.AzureCosmosDbMongoVCoreEmbeddingStore;

EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

AzureCosmosDbMongoVCoreEmbeddingStore store = AzureCosmosDbMongoVCoreEmbeddingStore.builder()
        .connectionString(System.getenv("DOCUMENTDB_CONNECTION_STRING"))
        .databaseName("rag")
        .collectionName("docs")
        .indexName("vectorIndex")
        .applicationName("langchain4j-quickstart")
        .createIndex(true)
        .kind("vector-hnsw")        // or "vector-ivf"
        .dimensions(embeddingModel.dimension())
        .m(16)
        .efConstruction(64)
        .efSearch(40)
        .build();

TextSegment segment = TextSegment.from("Azure DocumentDB is MongoDB-compatible.");
Embedding embedding = embeddingModel.embed(segment).content();
store.add(embedding, segment);

Embedding query = embeddingModel.embed("What is Azure DocumentDB?").content();
EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
        .queryEmbedding(query)
        .maxResults(3)
        .build());

for (EmbeddingMatch<TextSegment> match : result.matches()) {
    System.out.printf("%.3f  %s%n", match.score(), match.embedded().text());
}
```

### Index kinds

The `kind` builder option selects the underlying ANN algorithm:

- `vector-ivf` — IVF index. Tune with `numLists`.
- `vector-hnsw` — HNSW index. Tune with `m`, `efConstruction`,
  `efSearch`.

Pick the value of `dimensions` to match your embedding model
(for example, `1536` for OpenAI `text-embedding-3-small`).

## Spring Boot starter

If you use Spring Boot, add one of the starter modules instead of
configuring the store manually:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-azure-cosmos-mongo-vcore-spring-boot-starter</artifactId>
    <version>1.14.1-beta24</version>
</dependency>
```

(For Spring Boot 4, use
`langchain4j-azure-cosmos-mongo-vcore-spring-boot4-starter`.)

Then configure the store through `application.properties` /
`application.yml`. The configuration prefix
`langchain4j.azure.cosmos-mongo-vcore` is preserved so existing
configurations keep working:

```properties
langchain4j.azure.cosmos-mongo-vcore.connection-string=${DOCUMENTDB_CONNECTION_STRING}
langchain4j.azure.cosmos-mongo-vcore.database-name=rag
langchain4j.azure.cosmos-mongo-vcore.collection-name=docs
langchain4j.azure.cosmos-mongo-vcore.index-name=vectorIndex
langchain4j.azure.cosmos-mongo-vcore.application-name=langchain4j-quickstart
langchain4j.azure.cosmos-mongo-vcore.create-index=true
langchain4j.azure.cosmos-mongo-vcore.kind=vector-hnsw
langchain4j.azure.cosmos-mongo-vcore.dimensions=384
langchain4j.azure.cosmos-mongo-vcore.m=16
langchain4j.azure.cosmos-mongo-vcore.ef-construction=64
langchain4j.azure.cosmos-mongo-vcore.ef-search=40
```

`AzureCosmosDbMongoVCoreEmbeddingStore` is then auto‑wired and can be
injected as an `EmbeddingStore<TextSegment>` bean.

## Examples

- [AzureCosmosDBMongoVCoreEmbeddingStoreIT](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-azure-cosmos-mongo-vcore/src/test/java/dev/langchain4j/store/embedding/azure/cosmos/mongo/vcore/AzureCosmosDBMongoVCoreEmbeddingStoreIT.java)

## See also

- [Azure DocumentDB documentation](https://learn.microsoft.com/azure/documentdb/)
- [Vector search in Azure DocumentDB](https://learn.microsoft.com/azure/documentdb/vector-search)
