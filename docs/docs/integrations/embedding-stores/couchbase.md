---
sidebar_position: 7
---

# Couchbase

https://www.couchbase.com/


## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-couchbase</artifactId>
    <version>0.34.0</version>
</dependency>
```

## APIs

- `CouchbaseEmbeddingStore`


## Examples

- [CouchbaseEmbeddingStoreExample](https://github.com/langchain4j/langchain4j-examples/blob/main/couchbase-example/src/main/java/CouchbaseEmbeddingStoreExample.java)

## Couchbase Embedding Store
Couchbase langchain4j integration stores each embedding in a separate document and uses an FTS vector index to perform
queries against stored vectors. Currently, it supports storing embeddings and their metadata, as well as removing
embeddings. Filtering selected by vector search embeddings by their metadata was not supported at the moment of writing
this tutorial. Please note that the embedding store integration is still under active development and the default
configurations it comes with are not recommended for production usage.

### Connecting To Couchbase Cluster
A builder class can be used to initialize couchbase embedding store. The following parameters are required for
initialization:
- cluster connection string
- cluster username
- cluster password
- name of the bucket in which embeddings should be stored
- name of the scope in which embeddings should be stored
- name of the collection in which embeddings should be stored
- name of an FTS vector index to be used by the embedding store
- dimensionality (length) of vectors to be stored

The following sample code illustrates how to initialize an embedding store that connects to a locally running Couchbase
server:

```java
CouchbaseEmbeddingStore embeddingStore = CouchbaseEmbeddingStore.builder()
        .clusterUrl("localhost:8091")
        .username("Administrator")
        .password("password")
        .bucketName("langchain4j")
        .scopeName("_default")
        .collectionName("_default")
        .searchIndexName("test")
        .dimensions(512)
        .build();
```

The sample source code starts a dedicated to it Couchbase server using `testcontainers` library:

```java
CouchbaseContainer couchbaseContainer =
        new CouchbaseContainer(DockerImageName.parse("couchbase:enterprise").asCompatibleSubstituteFor("couchbase/server"))
                .withCredentials("Administrator", "password")
                .withBucket(testBucketDefinition)
                .withStartupTimeout(Duration.ofMinutes(1));

CouchbaseEmbeddingStore embeddingStore = CouchbaseEmbeddingStore.builder()
        .clusterUrl(couchbaseContainer.getConnectionString())
        .username(couchbaseContainer.getUsername())
        .password(couchbaseContainer.getPassword())
        .bucketName(testBucketDefinition.getName())
        .scopeName("_default")
        .collectionName("_default")
        .searchIndexName("test")
        .dimensions(384)
        .build();
```

### Vector Index
The embedding store uses an FTS vector index in order to perform vector similarity lookups. If provided with a name for
vector index that does not exist on the cluster, the store will attempt to create a new index with default
configuration based on the provided initialization settings. It is recommended to manually review the settings for the
created index and adjust them according to specific use cases. More information about vector search and FTS index
configuration can be found at [Couchbase Documentation](https://docs.couchbase.com/server/current/vector-search/vector-search.html).

### Embedding Documents
The integration automatically assigns unique `UUID`-based identifiers to all stored embeddings. Here is
an example embedding document (with vector field values truncated for readability):

```json
{
  "id": "f4831648-07ca-4c77-a031-75acb6c1cf2f",
  "vector": [
    ...
    0.037255168,
    -0.001608681
  ],
  "text": "text",
  "metadata": {
    "some": "value"
  },
  "score": 0
}
```

These embeddings are generated with a selected by developers embedding model and resulting vector values are model-specific.

## Storing Embeddings in Couchbase
Embeddings generated with an embedding model can be stored in couchbase using `add` and `addAll` methods of the `CouchbaseEmbeddingStore`
class:
```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

TextSegment segment1 = TextSegment.from("I like football.");
Embedding embedding1 = embeddingModel.embed(segment1).content();
embeddingStore.add(embedding1, segment1);

TextSegment segment2 = TextSegment.from("The weather is good today.");
Embedding embedding2 = embeddingModel.embed(segment2).content();
embeddingStore.add(embedding2, segment2);

Thread.sleep(1000); // to be sure that embeddings were persisted
```

## Querying Relevant Embeddings
After adding some embeddings into the store, a query vector can be used to find relevant to it embeddings in the store.
Here, we're using the embedding model to generate a vector for the phrase "what is your favorite sport?". The obtained
vector is then being used to find the most relevant answer in the database:
```java
Embedding queryEmbedding = embeddingModel.embed("What is your favourite sport?").content();
List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(queryEmbedding, 1);
EmbeddingMatch<TextSegment> embeddingMatch = relevant.get(0);
```

The relevancy score and text of the selected answer can then be printed to the application output:
```java
System.out.println(embeddingMatch.score()); // 0.81442887
System.out.println(embeddingMatch.embedded().text()); // I like football.
```

## Deleting Embeddings
Couchbase embedding store also supports removing embeddings by their identifiers, for example:
```java
embeddingStore.remove(embeddingMatch.id())
```

Or, to remove all embeddings:
```java
embeddingStore.removeAll();
```
