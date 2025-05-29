---
sidebar_position: 15
---

# MongoDB Atlas

[MongoDB Atlas](https://www.mongodb.com/docs/atlas/) is a fully-managed
cloud database available in AWS, Azure, and GCP. It supports native
Vector Search and full-text search (BM25 algorithm) on your MongoDB
document data.

The [Atlas Vector Search](https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-overview/)
feature allows you to store your embeddings in MongoDB documents, create
vector search indexes, and perform KNN search with an approximate
nearest neighbor algorithm called Hierarchical Navigable Small Worlds.
The MongoDB integration with LangChain4j implements Atlas Vector Search
internally by using the
[`$vectorSearch`](https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-stage/#mongodb-pipeline-pipe.-vectorSearch)
aggregation stage.

You can use Atlas Vector Search with LangChain4j to perform semantic
searches on your data and build a simple RAG implementation. To view a
full tutorial on performing these tasks, see the [Get Started with the
LangChain4j Integration](https://www.mongodb.com/docs/atlas/atlas-vector-search/ai-integrations/langchain4j/)
tutorial in the MongoDB Atlas documentation.

## Prerequisites

You must have a deployment running the following MongoDB Server versions
to use Atlas Vector Search:

-   6.0.11 or later
-   7.0.2 or later

MongoDB offers a free forever cluster. See the [Get Started with
Atlas](https://www.mongodb.com/docs/atlas/getting-started/) tutorial to
learn more about setting up an account and connecting to a deployment.

You also must have an API key with credits for an LLM service that has
provides embedding models, such as [Voyage
AI](https://www.voyageai.com/), which offers a free tier. For RAG
applications, you must also an API key for a service that has chat model
functionality, such as [OpenAI](https://openai.com/api/) or models from
[HuggingFace](https://huggingface.co/).

## Environment and Installation

1. Create a new Java application in your preferred IDE.
2. Add the following dependencies to your application to install
   LangChain4j and the MongoDB Java Sync Driver:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-mongodb-atlas</artifactId>
</dependency>
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongodb-driver-sync</artifactId>
    <version>5.4.0</version>
</dependency>
```

You must also install a dependency for your embedding model, for example
Voyage AI:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-voyage-ai</artifactId>
</dependency>
```

We also recommend adding the LangChain4j BOM:

```xml
<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-bom</artifactId>
        <version>1.0.1-beta6</version>
        <type>pom</type>
    </dependency>
</dependencyManagement>
```

## Use MongoDB Atlas as an Embedding Store

1. Instantiate an [embedding model](https://docs.langchain4j.dev/category/embedding-models).
2. Instantiate MongoDB Atlas as the embedding store.

You can enable automatic index creation by passing `true` to the
`createIndex()` method when building the `MongoDbEmbeddingStore`
instance.

```java
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.mongodb.IndexMapping;
import dev.langchain4j.store.embedding.mongodb.MongoDbEmbeddingStore;
import org.bson.Document;

import java.io.*;
import java.util.*;

String embeddingApiKey = System.getenv("VOYAGE_AI_KEY");
String uri = System.getenv("MONGODB_URI");

EmbeddingModel embeddingModel = VoyageAiEmbeddingModel.builder()
        .apiKey(embeddingApiKey)
        .modelName("voyage-3")
        .build();

MongoClient mongoClient = MongoClients.create(uri);

System.out.println("Instantiating the embedding store...");

// Set to false if the vector index already exists
Boolean createIndex = true;

IndexMapping indexMapping = IndexMapping.builder()
        .dimension(embeddingModel.dimension())
        .metadataFieldNames(new HashSet<>())
        .build();

MongoDbEmbeddingStore embeddingStore = MongoDbEmbeddingStore.builder()
        .databaseName("search")
        .collectionName("langchaintest")
        .createIndex(createIndex)
        .indexName("vector_index")
        .indexMapping(indexMapping)
        .fromClient(mongoClient)
        .build();
```

## Store Data in MongoDB

This code demonstrates how to persist your documents to the
embedding store. The `embed()` method generates embeddings for the `text`
field value in your documents.

```java
ArrayList<Document> docs = new ArrayList<>();

docs.add(new Document()
        .append("text", "Penguins are flightless seabirds that live almost exclusively below the equator. Some island-dwellers can be found in warmer climates.")
        .append("metadata", new Metadata(Map.of("website", "Science Direct"))));

docs.add(new Document()
        .append("text", "Emperor penguins are amazing birds. They not only survive the Antarctic winter, but they breed during the worst weather conditions on earth.")
        .append("metadata", new Metadata(Map.of("website", "Our Earth"))));

docs.add(...);

System.out.println("Persisting document embeddings...");

for (Document doc : docs) {
    TextSegment segment = TextSegment.from(
            doc.getString("text"),
            doc.get("metadata", Metadata.class)
    );
    Embedding embedding = embeddingModel.embed(segment).content();
    embeddingStore.add(embedding, segment);
}
```

## Perform Semantic/Similarity Searches

This code demonstrates how to create a search request that converts your
query into a vector and returns semantically similar documents. The
resulting `EmbeddingMatch` instances contain the document contents as
well as a score that describes how well each result matches your query.

```java
String query = "Where do penguins live?";
Embedding queryEmbedding = embeddingModel.embed(query).content();

EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(3)
        .build();

System.out.println("Performing the query...");

EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

for (EmbeddingMatch<TextSegment> embeddingMatch : matches) {
    System.out.println("Response: " + embeddingMatch.embedded().text());
    System.out.println("Author: " + embeddingMatch.embedded().metadata().getString("author"));
    System.out.println("Score: " + embeddingMatch.score());
}
```

### Metadata Filtering

You can implement metadata filtering by using the `filter()` method when
building a `EmbeddingSearchRequest`. The `filter()` method takes a
parameter that inherits from
[Filter](https://docs.langchain4j.dev/apidocs/dev/langchain4j/store/embedding/filter/Filter.html).

This code implements metadata filtering for only documents in which the
value of `website` is one of the listed values.

```java
EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .filter(new IsIn("website", List.of("Our Earth", "Natural Habitats")))
        .maxResults(3)
        .build();
```

## RAG

To view instructions on implementing RAG with MongoDB Atlas as your
vector store, see the [Use Your Data to Answer Questions](https://www.mongodb.com/docs/atlas/atlas-vector-search/ai-integrations/langchain4j/#use-your-data-to-answer-questions)
section of the LangChain4j tutorial in the Atlas documentation.

## API Documentation

-   [MongoDB Atlas Embedding Store Integration](https://docs.langchain4j.dev/apidocs/dev/langchain4j/store/embedding/mongodb/package-summary.html)

-   [MongoDB Java Sync Driver](https://mongodb.github.io/mongo-java-driver/5.4/apidocs/mongodb-driver-sync/index.html)

## Useful Links

-   [Get Started with the LangChain4j Integration](https://www.mongodb.com/docs/atlas/atlas-vector-search/ai-integrations/langchain4j/)
-   [How to Make a RAG Application With LangChain4j](https://dev.to/mongodb/how-to-make-a-rag-application-with-langchain4j-1mad)
