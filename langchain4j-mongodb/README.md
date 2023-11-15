# LangChain4J MongoDB Embedding Store

## Overview

Welcome to the LangChain4J MongoDB Embedding Store! This provides seamless integration between LangChain4J
and MongoDB, allowing you to use MongoDB as an embedding store.

## Features

- **Embedding Storage:** Store and retrieve embeddings in MongoDB.
- **Efficient Querying:** Leverage MongoDB's querying capabilities for efficient retrieval of embeddings.

## Getting Started

### Prerequisites

Make sure you have the following prerequisites installed:

- Java 8 or higher
- LangChain4J library
- MongoDB Atlas account (cloud.mongodb.com)

**remark**: it does not work on a standard MongoDB installation, you need to use MongoDB Atlas (cloud service).

### Installation

1. Add the LangChain4J MongoDB Embedding Store to your project:

   ```xml
   <!-- Maven Dependency -->
      <dependency>
         <groupId>dev.langchain4j</groupId>
         <artifactId>langchain4j-mongodb</artifactId>
         <version>0.23.0</version>
     </dependency>
   ```

    2. Create embedding store configuration and LangChain4J model:

       ```java
          EmbeddingStore<TextSegment> embeddingStore =  MongoDBEmbeddingStore.withUri(MONGODB_URI, "database", "collection")
                 .indexName("vectorsearch") // optional, default "default"
                 .build();
       ```

       or if you already hava a mongoDB client (from a Spring context):

       ```java
          // use existing MongoClient
          EmbeddingStore<TextSegment> embeddingStore =  MongoDBEmbeddingStore.withMongoDBClient(mongoClient, "database", "collection")
                 .indexName("vectorsearch") // optional, default "default"
                 .build();
       ```

3. Start using LangChain4J with MongoDB embedding storage!

## Example Usage

```java

EmbeddingModel embeddingModel=new AllMiniLmL6V2EmbeddingModel();

        EmbeddingStore<TextSegment> embeddingStore=MongoDBEmbeddingStore.withUri(MONGODB_URI,DATABASE,COLLECTION)
        .indexName(INDEX_NAME)
        .build();

        EmbeddingStoreIngestor ingestor=EmbeddingStoreIngestor.builder()
        .documentSplitter(DocumentSplitters.recursive(500,0))
        .embeddingModel(embeddingModel)
        .embeddingStore(embeddingStore)
        .build();

        Document document=loadDocument(toPath("story-about-happy-carrot.txt"));
        ingestor.ingest(document);

        ConversationalRetrievalChain chain=ConversationalRetrievalChain.builder()
        .chatLanguageModel(OpenAiChatModel.withApiKey(ApiKeys.OPENAI_API_KEY))
        .retriever(EmbeddingStoreRetriever.from(embeddingStore,embeddingModel))
        // .chatMemory() // you can override default chat memory
        // .promptTemplate() // you can override default prompt template
        .build();

        String answer=chain.execute("Who is Charlie?");
        System.out.println(answer); // Charlie is a cheerful carrot living in VeggieVille...
```

## Documentation

To set up a index in MongoDB Atlas, please follow
the [MongoDB Atlas documentation](https://www.mongodb.com/docs/atlas/atlas-search/field-types/knn-vector/).

A good starting point is to read the
tutorial: https://www.mongodb.com/developer/products/atlas/semantic-search-mongodb-atlas-vector-search/#configure-index.
It is beyond the scope of langchain4j but it explains the index setup very well.

### Configuration

The minimal index mapping on MongoDB Atlas:

```
   {
     "mappings": {
       "dynamic": false,
       "fields": {
         "embedding": {
           "dimensions": 1536,
           "similarity": "cosine",
           "type": "knnVector"
         }
       }
     }
   }

```

Where `dimensions` is the dimension of the embedding vector.

| Model                       | Dimension |
|-----------------------------|-----------|
| OpenAiEmbeddingModel        | 1536      |
| AllMiniLmL6V2EmbeddingModel | 384       |



### Extra Tuning properties

MongoDB vector search uses a `numCandidates` parameter to limit the number of documents to search.
It's the number of nearest neighbors to use during the search. Value must be less than or equal to (<=) 10000. You can't specify a number less than the number of documents to return (limit).
In the MongoDBEmbeddingStore, the `numCandidates` is determined by the `maxResultRatio` parameter. Which is a ratio of the `maxResults` parameter while retrieving relevant matches.
By default, the `maxResultRatio` is set to 10, which means that the `numCandidates` is equal to `maxResults` * 10.

```java
          EmbeddingStore<TextSegment> embeddingStore =  MongoDBEmbeddingStore.withMongoDBClient(mongoClient, "database", "collection")
               .indexName("indexName")
               .maxResultRatio(3L)
               .build();
```
MongoDb recommends to set the `numCandidates` to 10 times the number of requested documents, feel free to tune it.

_"We recommend that you specify a number higher than the number of documents to return (limit) to increase accuracy although this might impact latency. For example, we recommend a ratio of ten to twenty nearest neighbors for a limit of only one document. This overrequest pattern is the recommended way to trade off latency and recall in your aNN searches, and we recommend tuning this on your specific dataset."_

#### Expirimental features

it's possible to use prefiltering. It can be a way to limit the documents to search. It's still under development as MongoDB apparently does not support embedded field searches at the moment.
```java          
            EmbeddingStore<TextSegment> embeddingStore =  MongoDBEmbeddingStore.withMongoDBClient(mongoClient, "database", "collection")
               .indexName("indexName")
               .filter(Filters.eq("embedded.metadata.document_type", "TXT"))
               .build();
```




Happy embedding with LangChain4J and MongoDB!




