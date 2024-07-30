---
sidebar_position: 2
---

# Astra DB

[Astra DB](https://www.datastax.com/products/datastax-astra) is a cloud-native, fully managed database-as-a-service (DBaaS) based on Apache Cassandra. It provides a scalable, performant and highly available database solution without the operational overhead of managing Cassandra clusters. Astra supports both SQL and NoSQL APIs, and includes features like backup and restore, monitoring and alerting, and access control. It enables developers to focus on application development while the database infrastructure is managed by Datastax.

## 1. Pre-requisites

### 1.1. Sign up for Astra DB

- Access [https://astra.datastax.com](https://astra.datastax.com) and register with `Google` or `Github` account. It is free to use. There is free forever tiers of up to 25$ of consumption every month.

![](https://awesome-astra.github.io/docs/img/astra/astra-signin-github-0.png)

### 1.2. Create a Database

> If you are creating a new account, you will be brought to the DB-creation form directly.

- Get to the databases dashboard (by clicking on Databases in the left-hand navigation bar, expanding it if necessary), and click the `[Create Database]` button on the right.

![](https://datastaxdevs.github.io/langchain4j/langchain4j-1.png)

- **ℹ️ Fields Description**

| Field                                      | Description                                                                                                                                                                                                                                   |
|--------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Vector Database vs Serverless Database** | Choose `Vector Database` In june 2023, Cassandra introduced the support of vector search to enable Generative AI use cases.                                                                                                                   |
| **Database name**                          | It does not need to be unique, is not used to initialize a connection, and is only a label (keep it between 2 and 50 characters). It is recommended to have a database for each of your applications. The free tier is limited to 5 databases. |
| **Cloud Provider**                         | Choose whatever you like. Click a cloud provider logo, pick an Area in the list and finally pick a region. We recommend choosing a region that is closest to you to reduce latency. In free tier, there is very little difference.            |
| **Cloud Region**                           | Pick region close to you available for selected cloud provider and your plan.                                                                                                                                                                 |

If all fields are filled properly, clicking the "Create Database" button will start the process. 

![](https://datastaxdevs.github.io/langchain4j/langchain4j-2.png)

It should take a couple of minutes for your database to become `Active`.

![](https://datastaxdevs.github.io/langchain4j/langchain4j-3.png)

### 1.3. Get your credentials

To connect to your database, you need the API Endpoint and a token. The api endpoint is available on the database screen, there is a little icon to copy the URL in your clipboard. (it should look like `https://<db-id>-<db-region>.apps.astra.datastax.com`).

![](https://datastaxdevs.github.io/langchain4j/langchain4j-4.png)

To get a token click the `[Generate Token]` button on the right. It will generate a token that you can copy to your clipboard.

![](https://datastaxdevs.github.io/langchain4j/langchain4j-5.png)

## 2. AstraDB `EmbeddingStore`

> The full documentation regarding AstraDB can be found [here](https://docs.datastax.com/en/astra/astra-db-vector/api-reference/dataapiclient.html).
 
### 2.1. Connecting to Astra

The following code show how to initialize the client and access the database.

```java
String token = System.getenv("ASTRA_DB_APPLICATION_TOKEN");
String apiEndpoint = System.getenv("ASTRA_DB_API_ENDPOINT");

// Initialize the client. The keyspace parameter is optional if you use
// "default_keyspace".
DataAPIClient client = new DataAPIClient(token);

// Accessing the Database
Database db = client.getDatabase(astraApiEndpoint);
```

| Field                                                                                                                 | Description                                                                                                           |
|-----------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| **[DataAPIClient](https://datastaxdevs.github.io/astra-db-java/latest/com/datastax/astra/client/DataAPIClient.html)** | This class is the main entry point for the Astra Client. It allows to create databases and different admin operations |
| **[Database](https://datastaxdevs.github.io/astra-db-java/latest/com/datastax/astra/client/Database.html)**                                                                                                      | The API endpoint you copied in the previous step.                                                                     |

- From a Tenant (DataAPIClient) you can access one to many databases. The `Database` object is the entry point to interact with the database.
- 
- From a Database (Database) you can access one the many namespaces (logical). The default namespace is `default_keyspace.

### 2.2. AstraDB Collection

A Database can have one to multiple collections. A collection is a logical grouping of data. A collection can store different type of data abd can contains a `$vector` field. Those collections can be used to store any informations and not only vectors. It can then be used for ChatMemory or any cache needed.

> AstraDB collections can use different types of identifiers for its documents. Default is the UUIDv4 (java UUID) but more can be
> use like ObjectId (MongoDB) , UUIDv7 (Snowflake) or other type of identifier. To get the complete list consult the [documentation](https://docs.datastax.com/en/astra/astra-db-vector/api-reference/collections.html#the-defaultid-option)

In Langchain4j, `AstraDBEmbeddingStore` is associated to one Collection with a `$vector` field. The `$vector` field is used to store the embeddings. The following code shows how to create a collection with a `$vector` field. By default there is no special field to store the text segment of the chunk. By CONVENTION, the store use field name `content`.
```java
// Create a vector collection
Collection<Document> col = db.createCollection("langchain4j_embedding_store", 
 CollectionOptions
  .builder()
  .vectorDimension(1536)   // related to your embedding mode
  .vectorSimilarity(SimilarityMetric.COSINE) // 
  .indexingDeny("content") // avoid to index text segment
  .build());
```

If the collection exists (most of the time), you can access it with the following code:
```java
Collection<Document> col2 = db
        .getCollection("langchain4j_embedding_store");
```

### 2.3. Init EmbeddingStore

To initialize the `AstraDBEmbeddingStore` simply give the collection object as argument.

```java
EmbeddingStore<TextSegment> embeddingStore = new AstraDBEmbeddingStore(col);
```

We could provide this utility method to help with creation of the store:

```java
/**
 * Create an AstraDB Embedding Store.
 */
EmbeddingStore<TextSegment> createEmbeddingStore(
 String astraToken, String apiEndpoint, 
 String collectionName, int dimension, 
 SimilarityMetric metric) {

 return new AstraDBEmbeddingStore(
   // AstraDB Client
   new DataAPIClient(astraToken)
     .getDatabase(apiEndpoint)
     .createCollection(collectionName, dimension, metric));
}
```

### 2.4. Usage

Please find enclosed a simple example of how to use the `AstraDBEmbeddingStore`:

```java
// Given a embedding model
EmbeddingModel embeddingModel = ...;

// Given a text file on disk
Path textFile = ....;

// Get AstraDBEmbeddingStore
EmbeddingStore<TextSegment> embeddingStore = createEmbeddingStore(
  System.getenv("ASTRA_DB_APPLICATION_TOKEN"),
  System.getenv("ASTRA_DB_API_ENDPOINT"),
  "langchain4j_embedding_store",
  1536,
  SimilarityMetric.COSINE);
        
// Ingestion
EmbeddingStoreIngestor.builder()
  .documentSplitter(recursive(100, 10, new OpenAiTokenizer(GPT_3_5_TURBO)))
  .embeddingModel(embeddingModel)
  .embeddingStore(embeddingStore)
  .build()
  .ingest(loadDocument(textFile, new TextDocumentParser()));

// Sample Vector Search
ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
 .embeddingModel(embeddingModel)
 .embeddingStore(embeddingStore)
 .maxResults(2)
 .minScore(0.5)
 .build();

Assistant ai = AiServices.builder(Assistant.class)
 .contentRetriever(contentRetriever)
 .chatLanguageModel(initChatLanguageModelOpenAi())
 .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
 .build();
String response = ai.answer("What vegetable is Happy?");
 

// Meta-Data Filtering
RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor
  .builder()
  .contentRetriever(contentRetriever)
  .contentInjector(DefaultContentInjector.builder()
     .metadataKeysToInclude(asList("document_format",  "text"))
     .build())
  .build();

// configuring it to use the components we've created above.
Assistant aiWithMetaData = AiServices.builder(Assistant.class)
   .retrievalAugmentor(retrievalAugmentor)
   .chatLanguageModel(getChatLanguageModelChatBison())
   .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
   .build();
```



