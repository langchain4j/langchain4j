---
sidebar_position: 16
---



# Neo4j

[Neo4j](https://neo4j.com/) is a high-performance, open-source graph database designed for managing connected data.
Neo4j's native graph model is ideal for modelling complex and highly interconnected domains, like social graphs, recommendation systems, and knowledge networks.
With its integration in LangChain4j, the [Neo4j Vector](https://github.com/neo4j-documentation/labs-pages/blob/publish/modules/genai-ecosystem/pages/vector-search.adoc) capabilities can be used in the Langchain4j library.

## Maven Dependency
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-neo4j</artifactId>
    <version>${latest version here}</version>
</dependency>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-neo4j-retriever</artifactId>
    <version>${latest version here}</version>
</dependency>

<!-- if we want to use the Spring Boot starter -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-neo4j-spring-boot-starter</artifactId>
    <version>${latest version here}</version>
</dependency>
```
## APIs

LangChain4j provides the following classes for Neo4j integration:
- `Neo4jEmbeddingStore`:  Implements the EmbeddingStore interface, enabling storing and querying vector embeddings in a Neo4j database.
- `Neo4jText2CypherRetriever`:  Implements the ContentRetriever interface for generating and executing Cypher queries from user questions, improving content retrieval from Neo4j databases. It translates natural language questions into Cypher queries,
  leveraging the Neo4j schema calculated via [apoc.meta.data](https://neo4j.com/docs/apoc/current/overview/apoc.meta/apoc.meta.data) procedure.
- `KnowledgeGraphWriter`: A class that stores Neo4j nodes and relationships starting from structured data coming from `LLMGraphTransformer`, 
that is a tool that transform one or more unstructured documents in a graph. It’s database-agnostic, which means that  transforms texts into a set of Nodes and Edges that can also be used for other graph databases like RedisGraph.
- `Neo4jEmbeddingStoreIngestor`: Implements the `ParentChildEmbeddingStoreIngestor` interface, it performs a multi-stage transformation pipeline: it transforms documents, splits them into segments, optionally applies additional transformations to child segments, generates embeddings, and stores both the parent-child relationships and embeddings in Neo4j.
- `Neo4jChatMemoryStore`: Implements the `ChatMemoryStore` interface that stores and retrieves conversational messages in a Neo4j graph database. It supports managing chat history with efficient querying and persistence using Neo4j nodes and relationships.

## Usage Examples

### Neo4jEmbeddingStore

Here is how to create a `Neo4jEmbeddingStore` instance:

```java
Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder().<builderParameters>.build();
```

Where `<builderParameters>` must include `dimension` and either `driver` or `withBasicAuth` parameters, along with other optional ones.

Here is the complete builder list:

| Key                 | Default Value| Description        |
| ------------------- |-----| --------------------- |
| `driver`            | *Required if `withBasicAuth` is not set*   | The [Java Driver instance](https://neo4j.com/docs/api/java-driver/current/org.neo4j.driver/org/neo4j/driver/Driver.html) |
| `withBasicAuth`     | *Required if `driver` is not set*       | Creates a [Java Driver instance](https://neo4j.com/docs/api/java-driver/current/org.neo4j.driver/org/neo4j/driver/Driver.html) from `uri`, `user`, and `password` |
| `dimension`         | *Required*    | The vector's dimension  |
| `config`            | `org.neo4j.driver.SessionConfig.forDatabase("<databaseName>")`   | The [SessionConfig instance](https://neo4j.com/docs/api/java-driver/current/org.neo4j.driver/org/neo4j/driver/SessionConfig.html)                                |
| `label`             | `"Document"`| The label name    |
| `embeddingProperty` | `"embedding"` | The embedding property name |
| `idProperty`        | `"id"` | The ID property name  |
| `metadataPrefix`    | `""`       | The metadata prefix   |
| `textProperty`      | `"text"`  | The text property name |
| `indexName`         | `"vector"` | The vector index name  |
| `databaseName`      | `"neo4j"`| The database name  |
| `retrievalQuery`    | `"RETURN properties(node) AS metadata, node.idProperty AS idProperty, node.textProperty AS textProperty, node.embeddingProperty AS embeddingProperty, score"`  | The retrieval query     |




Therefore, to create `Neo4jEmbeddingStore` instance, you need to provide proper settings:
```java
// ---> MINIMAL EMBEDDING <---
Neo4jEmbeddingStore minimalEmbedding = Neo4jEmbeddingStore.builder()
    .withBasicAuth(NEO4J_CONNECTION_STRING, USERNAME, ADMIN_PASSWORD)
    .dimension(384)
    .build();

// ---> CUSTOM EMBEDDING <---
Neo4jEmbeddingStore customEmbeddingStore = Neo4jEmbeddingStore.builder()
        .withBasicAuth(NEO4J_CONNECTION_STRING, USERNAME, ADMIN_PASSWORD)
        .dimension(384)
        .indexName(CUSTOM_INDEX)
        .metadataPrefix(CUSTOM_METADATA_PREF)
        .label(CUSTOM_LABEL)
        .embeddingProperty(CUSTOM_PROP)
        .idProperty(CUSTOM_ID)
        .textProperty(CUSTOM_TEXT)
        .build();
```
Then you can add the embeddings in many different ways, and search them:
```java
// ---> ADD MINIMAL EMBEDDING <---
Embedding embedding = embeddingModel.embed("embedText").content();
String id = minimalEmbedding.add(embedding); // output: id of the embedding

// ---> ADD MINIMAL EMBEDDING WITH ID <---
String id = randomUUID();
Embedding embedding = embeddingModel.embed("embedText").content();
minimalEmbedding.add(id, embedding);

// ---> ADD EMBEDDING WITH SEGMENT <---
TextSegment segment = TextSegment.from(randomUUID());
Embedding embedding = embeddingModel.embed(segment.text()).content();
String id = minimalEmbedding.add(embedding, segment);

// ---> ADD EMBEDDING WITH SEGMENT AND METADATA <---
TextSegment segment = TextSegment.from(randomUUID(), Metadata.from(METADATA_KEY, "test-value"));
Embedding embedding = embeddingModel.embed(segment.text()).content();
String id = minimalEmbedding.add(embedding, segment);

// ---> ADD MULTIPLE EMBEDDINGS <---
Embedding firstEmbedding = embeddingModel.embed("firstEmbedText").content();
Embedding secondEmbedding = embeddingModel.embed("secondEmbedText").content();
List<String> ids = minimalEmbedding.addAll(asList(firstEmbedding, secondEmbedding));

// ---> ADD MULTIPLE EMBEDDINGS WITH SEGMENTS <---
TextSegment firstSegment = TextSegment.from("firstText");
Embedding firstEmbedding = embeddingModel.embed(firstSegment.text()).content();
TextSegment secondSegment = TextSegment.from("secondText");
Embedding secondEmbedding = embeddingModel.embed(secondSegment.text()).content();
List<String> ids = minimalEmbedding.addAll(
        asList(firstEmbedding, secondEmbedding),
        asList(firstSegment, secondSegment)
);
```
Then you can search the stored embeddings:
```java
// ---> SEARCH EMBEDDING WITH MAX RESULTS <---
String id = minimalEmbedding.add(embedding);
final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(embedding)
        .maxResults(10)
        .build();
final List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(request).matches();

// ---> SEARCH EMBEDDING WITH MIN SCORE <---
Embedding embedding = embeddingModel.embed("embedText").content();
String id = minimalEmbedding.add(embedding);
final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(embedding)
        .maxResults(10)
        .minScore(0.15)
        .build();
final List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(request).matches();

// ---> SEARCH EMBEDDING WITH CUSTOM METADATA PREFIX <---
String metadataCompleteKey = CUSTOM_METADATA_PREF + METADATA_KEY;
TextSegment segment = TextSegment.from(randomUUID(), Metadata.from(METADATA_KEY, "test-value"));
Embedding embedding = embeddingModel.embed(segment.text()).content();
String id = customEmbeddingStore.add(embedding, segment);
final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(embedding)
        .maxResults(10)
        .build();
final List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(request).matches();

// ---> SEARCH EMBEDDING WITH CUSTOM ID PROPERTY <---
String metadataCompleteKey = CUSTOM_METADATA_PREF + METADATA_KEY;
TextSegment segment = TextSegment.from(randomUUID(), Metadata.from(METADATA_KEY, "test-value"));
Embedding embedding = embeddingModel.embed(segment.text()).content();
String id = embeddingStore.add(embedding, segment);
final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .build();
final List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(request).matches();

// ---> SEARCH MULTIPLE EMBEDDING <---
List<String> ids = minimalEmbedding.addAll(asList(firstEmbedding, secondEmbedding));
final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(firstEmbedding)
        .maxResults(10)
        .build();
final List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(request).matches();

// ---> SEARCH MULTIPLE EMBEDDING WITH SEGMENTS <---
List<String> ids = minimalEmbedding.addAll(
        asList(firstEmbedding, secondEmbedding),
        asList(firstSegment, secondSegment)
);
final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(firstEmbedding)
        .maxResults(10)
        .build();
final List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.search(request).matches();
```

To get embeddings using a hybrid search leveraging both the vector and the full text index:
```java
// ---> ADDS EMBEDDING AND FULLTEXT WITH ID <---
embeddingStore = Neo4jEmbeddingStore.builder()
        .withBasicAuth("<Bolt URL>", "<username>", "<password>")
        .dimension(384)
        .fullTextIndexName("movie_text")
        .fullTextQuery("Matrix")
        .autoCreateFullText(true)
        .label(LABEL_TO_SANITIZE)
        .build();

List<Embedding> embeddings =
        embeddingModel.embedAll(List.of(TextSegment.from("test"))).content();
        embeddingStore.addAll(embeddings);

final Embedding queryEmbedding = embeddingModel.embed("Matrix").content();

final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(1)
        .build();

final List<EmbeddingMatch<TextSegment>> matches =
        embeddingStore.search(embeddingSearchRequest).matches();

// ---> SEARCH EMBEDDING WITH AUTOCREATED FULLTEXT <---
final String fullTextIndexName = "movie_text";
final String label = "Movie";
final String fullTextSearch = "Matrix";
embeddingStore = Neo4jEmbeddingStore.builder()
        .withBasicAuth("<Bolt URL>", "<username>", "<password>")
        .dimension(384)
        .label(label)
        .indexName("movie_vector_idx")
        .fullTextIndexName(fullTextIndexName)
        .fullTextQuery(fullTextSearch)
        .build();
```

If the FULLTEXT index is invalid, a descriptive exception will be thrown.: 
```java
// ---> ERROR HANDLING WITH INVALID FULLTEXT <---
Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
        .withBasicAuth("<Bolt URL>", "<username>", "<password>")
        .dimension(384)
        .fullTextIndexName("full_text_with_invalid_retrieval")
        .fullTextQuery("Matrix")
        .autoCreateFullText(true)
        .fullTextRetrievalQuery("RETURN properties(invalid) AS metadata")
        .label(LABEL_TO_SANITIZE)
        .build();

List<Embedding> embeddings = embeddingModel.embedAll(List.of(TextSegment.from("test"))).content();
embeddingStore.addAll(embeddings);

final Embedding queryEmbedding = embeddingModel.embed("Matrix").content();

final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(3)
        .build();
embeddingStore.search(embeddingSearchRequest).matches();
// This search will throw a ClientException: ... Variable `invalid` not defined ...
```

To execute a search with a metadata filtering leveraging the `dev.langchain4j.store.embedding.filter.Filter` class:
```java
// ---> ADD EMBEDDING WITH ID AND RETRIEVE WITH OR WITHOUT PREFILTER <---
final List<TextSegment> segments = IntStream.range(0, 10)
                .boxed()
                .map(i -> {
                    if (i == 0) {
                        final Map<String, Object> metas =
                                Map.of("key1", "value1", "key2", 10, "key3", "3", "key4", "value4");
                        final Metadata metadata = new Metadata(metas);
                        return TextSegment.from(randomUUID(), metadata);
                    }
                    return TextSegment.from(randomUUID());
                })
                .toList();

final List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
embeddingStore.addAll(embeddings, segments);

final And filter = new And(
        new And(new IsEqualTo("key1", "value1"), new IsEqualTo("key2", "10")),
        new Not(new Or(new IsIn("key3", asList("1", "2")), new IsNotEqualTo("key4", "value4"))));

TextSegment segmentToSearch = TextSegment.from(randomUUID());
Embedding embeddingToSearch =
        embeddingModel.embed(segmentToSearch.text()).content();
final EmbeddingSearchRequest requestWithFilter = EmbeddingSearchRequest.builder()
        .maxResults(5)
        .minScore(0.0)
        .filter(filter)
        .queryEmbedding(embeddingToSearch)
        .build();
final EmbeddingSearchResult<TextSegment> searchWithFilter = embeddingStore.search(requestWithFilter);
final List<EmbeddingMatch<TextSegment>> matchesWithFilter = searchWithFilter.matches();

final EmbeddingSearchRequest requestWithoutFilter = EmbeddingSearchRequest.builder()
        .maxResults(5)
        .minScore(0.0)
        .queryEmbedding(embeddingToSearch)
        .build();
final EmbeddingSearchResult<TextSegment> searchWithoutFilter = embeddingStore.search(requestWithoutFilter);
final List<EmbeddingMatch<TextSegment>> matchesWithoutFilter = searchWithoutFilter.matches();
```

To execute a follow-up query for reading or writing data retrieved by the embedding search, we can leverage the nodes' `embeddingId`s.
For example:
```java
// ... Neo4jEmbeddingStore instance creation ...
// ... add embeddings.... 

final List<EmbeddingMatch<TextSegment>> results = embeddingStore.search(/*dev.langchain4j.store.embedding.EmbeddingSearchRequest instance*/)
        .matches();

// retrieve the ids to execute the follow-up
List<String> nodeIds = results.stream().map(dev.langchain4j.store.embedding.EmbeddingMatch:embeddingId).toList();

String cypher = """
        MATCH (d:Document)
        WHERE d.id IN $ids
        // -- here the follow-up query, for example
        WITH (d)-[:CONNECTED_TO]->(o:OtherLabel) 
        RETURN o.id
    """;

// run the follow-up query
Map<String, Object> params = Map.of("ids", nodeIds);
final List<Record> list = session.run(cypher, params).list();
```

#### Spring Boot starter

To create a **Spring Boot starter**, the Neo4j starter provides at the time being the following `application.properties`:
```properties

# the builder.dimension(dimension) method
langchain4j.community.neo4j.dimension=<dimension>
# the builder.withBasicAuth(uri, username, password) method
langchain4j.community.neo4j.auth.uri=<boltURI>
langchain4j.community.neo4j.auth.user=<username>
langchain4j.community.neo4j.auth.password=<password>
# the builder.label(label) method
langchain4j.community.neo4j.label=<label>
# the builder.indexName(indexName) method
langchain4j.community.neo4j.indexName=<indexName>
# the builder.metadataPrefix(metadataPrefix) method
langchain4j.community.neo4j.metadataPrefix=<metadataPrefix>
# the builder.embeddingProperty(embeddingProperty) method
langchain4j.community.neo4j.embeddingProperty=<embeddingProperty>
# the builder.idProperty(idProperty) method
langchain4j.community.neo4j.idProperty=<idProperty>
# the builder.textProperty(textProperty) method
langchain4j.community.neo4j.textProperty=<textProperty>
# the builder.databaseName(databaseName) method
langchain4j.community.neo4j.databaseName=<databaseName>
# the builder.retrievalQuery(retrievalQuery) method
langchain4j.community.neo4j.retrievalQuery=<retrievalQuery>
# the builder.awaitIndexTimeout(awaitIndexTimeout) method
langchain4j.community.neo4j.awaitIndexTimeout=<awaitIndexTimeout>
```
Configuring the Starter allows us to create a simple Spring Boot project like the following:
```java
@SpringBootApplication
public class SpringBootExample {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootExample.class, args);
    }

    @Bean
    public AllMiniLmL6V2EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
    
}

@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingController {

    private final EmbeddingStore<TextSegment> store;
    private final EmbeddingModel model;

    public EmbeddingController(EmbeddingStore<TextSegment> store, EmbeddingModel model) {
        this.store = store;
        this.model = model;
    }

    // add embeddings
    @PostMapping("/add")
    public String add(@RequestBody String text) {
        TextSegment segment = TextSegment.from(text);
        Embedding embedding = model.embed(text).content();
        return store.add(embedding, segment);
    }

    // search embeddings
    @PostMapping("/search")
    public List<String> search(@RequestBody String query) {
        Embedding queryEmbedding = model.embed(query).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5)
                .build();
        return store.search(request).matches()
                .stream()
                .map(i -> i.embedded().text()).toList();
    }
}
```
We have defined APIs that can be called easily, as shown here:
```shell
# to create a new embedding 
# and store it with a label "Spring Boot"
curl -X POST localhost:8083/api/embeddings/add -H "Content-Type: text/plain" -d "embeddingTest"

# to search the first 5 embeddings
curl -X POST localhost:8083/api/embeddings/search -H "Content-Type: text/plain" -d "querySearchTest"
```


### Neo4jText2CypherRetriever

Here is how to create a `Neo4jText2CypherRetriever` instance:

```java
Neo4jText2CypherRetriever retriever = Neo4jText2CypherRetriever.builder().<builderParameters>.build();
````

Here is the complete builder list:

| Key | Default Value     | Description |
| ---------- |-------------------| ---------- |
| `graph`    | *Required*        | See below  |
| `chatModel` | *Required*        | The [ChatModel](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/model/chat/ChatModel.java) implementation used to create the Cypher query from a natural language question |
| `prompt`   | See example below | The prompt that will be used with the chatModel |
| `examples` | Empty string      | Additional examples to enrich and improve the result |
| `maxRetries` | 3                 | Additional retry to generate the Cypher query if it fails or returns an empty result                                                                                                                                           |

To connect to Neo4j we have to leverage the `Neo4jGraph` class this way:

```java
// Neo4j Java Driver connection instance
Driver driver = GraphDatabase.driver("<Bolt URL>", AuthTokens.basic("<username>", "<password>"));

Neo4jGraph neo4jGraph = Neo4jGraph.builder()
    .driver(driver)
    .build();
```

Or using `withBasicAuth` as with the `Neo4jEmbeddingStore`:

```java
Neo4jGraph neo4jGraph = Neo4jGraph.builder()
    .withBasicAuth("<Bolt URL>", "<username>", "<password>")
    .build();
```

And then pass it to the builder:

```java
Neo4jGraph neo4jGraph = /* Neo4jGraph instance */;

// ChatModel instance, e.g. OpenAiChatModel
ChatModel chatLanguageModel = OpenAiChatModel.builder()
        .apiKey(OPENAI_API_KEY)
        .modelName(GPT_4_O_MINI)
        .build();

// Neo4jText2CypherRetriever instance
Neo4jText2CypherRetriever retriever = Neo4jText2CypherRetriever.builder()
        .graph(neo4jGraph)
        .chatLanguageModel(chatLanguageModel)
        .build();
```

You can further customize the `Neo4jGraph` behavior by adjusting parameters such as `sample` (how many example paths to return in the context prompt) and `maxRels` (the maximum number of relationships to read per node label).
These parameters are optional (with default respectively `1000` and `100`) and can be omitted if you prefer the default behavior.
These are particularly useful for controlling prompt size and complexity in larger graphs.

Moreover, you can use the `Neo4jGraph` to return the entities schema, 
i.e. a list of patterns, node properties, and relationship properties that describe the structure of the graph:

```java
final Neo4jGraph.StructuredSchema structuredSchema = graph.getStructuredSchema();

List<String> patterns = structuredSchema.patterns();
List<String> nodesProperties = structuredSchema.nodesProperties();
List<String> relationshipsProperties = structuredSchema.relationshipsProperties();

/*
Example outputs:
`patterns`: [(:Person)-[:WROTE]->(:Book)]
`nodesProperties`: [:Book {title: STRING}, :Person {name: STRING}]
`relationshipsProperties`: [:WROTE {year: 1986}]
*/
```

### Example with `sample` and `maxRels`

```java
Neo4jGraph neo4jGraph = Neo4jGraph.builder()
    .driver(driver)
    .sample(3L) // Sample up to 3 example paths from the graph schema
    .maxRels(8L) // Explore a maximum of 8 relationships from the start node
    .build();

Neo4jText2CypherRetriever retriever = Neo4jText2CypherRetriever.builder()
    .graph(neo4jGraph)
    .chatLanguageModel(chatLanguageModel)
    .build();
```


Here is a basic examples:
```java

// create dataset, for example:
// CREATE (book:Book {title: 'Dune'})<-[:WROTE {when: date('1999')}]-(author:Person {name: 'Frank Herbert'})");


// create a Neo4jGraph instance
Neo4jGraph neo4jGraph = Neo4jGraph.builder()
        .driver(/*<Neo4j Driver instance>*/)
        .build();

// create a Neo4jText2CypherRetriever instance
Neo4jText2CypherRetriever retriever = Neo4jText2CypherRetriever.builder()
        .graph(neo4jGraph)
        .chatLanguageModel(chatLanguageModel)
        .build();

Query query = new Query("Who is the author of the book 'Dune'?");

// retrieve result
List<Content> contents = retriever.retrieve(query);

System.out.println(contents.get(0).textSegment().text());
// example output: "Frank Herbert"
```
The above one will execute a chat request with the following prompt string:
```text
Task:Generate Cypher statement to query a graph database.
Instructions
Use only the provided relationship types and properties in the schema.
Do not use any other relationship types or properties that are not provided.
Schema:

Node properties are the following:
:Book {title: STRING}
:Person {name: STRING}

Relationship properties are the following:
:WROTE {when: DATE}

The relationships are the following:
(:Person)-[:WROTE]->(:Book)

Note: Do not include any explanations or apologies in your responses.
Do not respond to any questions that might ask anything else than for you to construct a Cypher statement.
Do not include any text except the generated Cypher statement.
The question is: {{question}}
```
where  `question` is "Who is the author of the book 'Dune'?"
and `schema` is handled by the apoc.meta.data procedure to retrieve and stringify the current Neo4j schema.
In this case is
```text
Node properties are the following:
:Book {title: STRING}
:Person {name: STRING}

Relationship properties are the following:
:WROTE {when: DATE}

The relationships are the following:
(:Person)-[:WROTE]->(:Book)
----

We can also change the default prompt if needed:
[source,java]
----
Neo4jGraph neo4jGraph = /* Neo4jGraph instance */

Neo4jText2CypherRetriever.builder()
  .neo4jGraph(neo4jGraph)
  .promptTemplate("<custom prompt>")
  .build();
```


To create a retriever without any retry logic, set `maxRetries` to `0`:

```java
Neo4jText2CypherRetriever retriever = Neo4jText2CypherRetriever.builder()
    .graph(graph)
    .chatModel(chatModel)
    .maxRetries(0) // disables retry logic
    .build();
```
This configuration is useful when you want deterministic behavior and do not want the retriever to attempt fallback queries if the Cypher generation fails. It’s typically recommended for scenarios where performance is critical or failure handling is managed externally.


Also use the `fromLLM("<question>")` method to leverage a `chatModel` with the following prompt to generate a natural language answer based on the retrieved context and Cypher query, where `{{context}}` is the schema retrieved from `Neo4jGraph`, `{{cypher}}` is the Cypher query generated by text-to-Cypher, and `{{question}}` is the argument passed to `fromLLM()`.
```
Based on the following context and the generated Cypher,
write an answer in natural language to the provided user's question:
Context: {{context}}
Generated Cypher: {{cypher}}
Question: {{question}}
Cypher query:
````

Example usage:

```java
Neo4jText2CypherRetriever neo4jContentRetriever = Neo4jText2CypherRetriever.builder()
        .graph(graph)
        .chatModel(OPEN_AI_CHAT_MODEL)
        .build();

Query query = new Query("Who is the author of the book 'Dune'?");

String response = neo4jContentRetriever.fromLLM(query);
// example output: the author of the book 'Dune' is Frank Herbert

````



### KnowledgeGraphWriter

The `KnowledgeGraphWriter` is a utility class for writing structured knowledge graph data to Neo4j. It is designed to work with data produced by an `LLMGraphTransformer`, which extracts nodes and relationships from unstructured documents.

This writer is particularly useful for scenarios where textual data has been transformed into a graph structure and needs to be stored efficiently in a Neo4j database, including optional document provenance.

#### Features

- Stores nodes and relationships in Neo4j from `GraphDocument` instances.
- Supports optional storage of source document metadata and content.
- Automatically creates unique constraints for entities.
- Allows customization of labels, relationship types, ID and text properties.

Here is how to create a `KnowledgeGraphWriter` instance:

```java
KnowledgeGraphWriter writer = KnowledgeGraphWriter.builder().<builderParameters>.build();
```

#### Here is the complete builder list:

| Builder Method           | Description                                                | Default Value    |
| ------------------------ | ---------------------------------------------------------- | ---------------- |
| `graph(Neo4jGraph)`      | Sets the Neo4j graph connection. (Required)                | -                |
| `label(String)`          | Sets the entity label for nodes.                           | `__Entity__`     |
| `relType(String)`        | Sets the relationship type between entities and documents. | `HAS_ENTITY`     |
| `idProperty(String)`     | Sets the property name used as the unique identifier.      | `id`             |
| `textProperty(String)`   | Sets the property name used for storing document text.     | `text`           |
| `constraintName(String)` | Sets the name of the uniqueness constraint in Neo4j.       | `knowledge_cons` |



```java
Neo4jGraph graph = Neo4jGraph.builder()
    .withBasicAuth("bolt://localhost:7687", "neo4j", "password")
    .build();

KnowledgeGraphWriter writer = KnowledgeGraphWriter.builder()
    .graph(graph)
    .label("Entity")
    .relType("MENTIONS")
    .idProperty("id")
    .textProperty("text")
    .build();

List<GraphDocument> graphDocuments = ... // obtained from LLMGraphTransformer
writer.addGraphDocuments(graphDocuments, true); // set to true to include document source
````


### Neo4jEmbeddingStoreIngestor

`Neo4jEmbeddingStoreIngestor` is a specialized ingestor class designed to store embeddings and related data in a Neo4j graph database. It provides configurable options for embedding storage, query templates, and prompts to support various knowledge ingestion and retrieval workflows.

Here is how to create a `Neo4jEmbeddingStoreIngestor` instance:

```java
Neo4jEmbeddingStoreIngestor ingestor = Neo4jEmbeddingStoreIngestor.builder()
    .<builderParameters>
    .build();
```

Where the `<builderParameters>` includes `driver` and `dimension` as required, plus optional customization.

Here is the complete builder list:

| Key                   | Default Value             | Description                                                                                                                    |
| --------------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `driver`              | *Required*                | The [Neo4j Java Driver instance](https://neo4j.com/docs/api/java-driver/current/org.neo4j.driver/org/neo4j/driver/Driver.html) |
| `retrievalQuery`      | See class default         | Cypher query used to retrieve entities during embedding lookup                                                                 |
| `entityCreationQuery` | See class default         | Cypher query for creating entities with embeddings                                                                             |
| `label`               | `"Child"`                 | Node label to use in Neo4j for embedding nodes                                                                                 |
| `indexName`           | `"child_embedding_index"` | Name of the index for embedding nodes                                                                                          |
| `dimension`           | `384`                     | Dimensionality of the embedding vectors                                                                                        |
| `systemPrompt`        | See class default         | System prompt for LLM-driven tasks                                                                                             |
| `userPrompt`          | See class default         | User prompt for LLM-driven tasks                                                                                               |


**Basic usage with required parameters:**

```java
Neo4jEmbeddingStoreIngestor ingestor = Neo4jEmbeddingStoreIngestor.builder()
    .driver(neo4jDriver)
    .dimension(384)
    .build();
```

**Custom retrieval and creation queries:**

```java
Neo4jEmbeddingStoreIngestor ingestor = Neo4jEmbeddingStoreIngestor.builder()
    .driver(neo4jDriver)
    .dimension(384)
    .retrievalQuery("MATCH (doc:Document) WHERE doc.id = $id RETURN doc")
    .entityCreationQuery("CREATE (doc:Document {id: $id, embedding: $embedding})")
    .label("Document")
    .indexName("document_embedding_index")
    .build();
```

**Using custom system and user prompts:**

```java
Neo4jEmbeddingStoreIngestor ingestor = Neo4jEmbeddingStoreIngestor.builder()
    .driver(neo4jDriver)
    .dimension(384)
    .systemPrompt("You are an expert knowledge base ingestor.")
    .userPrompt("Please ingest the following content:")
    .build();
```


### Neo4j Ingestors for Specialized Use Cases

The following classes extend `Neo4jEmbeddingStoreIngestor` to provide pre-configured ingestion logic tailored to specific [GraphRAG](https://graphrag.com/reference/graphrag) patterns. Each ingestor comes with predefined Cypher queries and prompt templates, while still allowing builder-level customization.
All ingestors inherit the full builder API from `Neo4jEmbeddingStoreIngestor`.

#### SummaryGraphIngestor


To implement the [Global Community Summary Retriever concept](https://graphrag.com/reference/graphrag/global-community-summary-retriever/)
This ingestor extracts and stores concise summaries of documents in the graph using summarization prompts and stores them as nodes labeled `"Summary"` (by default), linked to the original document.

Example usage:
```java
SummaryGraphIngestor ingestor = SummaryGraphIngestor.builder()
        .driver(driver)
        .embeddingModel(embeddingModel)
        .questionModel(chatModel)
        .documentSplitter(splitter)
        .build();
````

Unlike `Neo4jEmbeddingStoreIngestor`, it has the following default values:

- `query`: `"CREATE (:SummaryChunk $metadata)"`
- `systemPrompt`:
```text
You are generating concise and accurate summaries based on the information found in the text.
```

- `userPrompt`:
```text
Generate a summary of the following input:
{{input}}

Summary:
```

- `embeddingStore`:
```java
private static final String DEFAULT_RETRIEVAL = """
        MATCH (node)<-[:HAS_SUMMARY]-(parent)
        WITH parent, max(score) AS score, node // deduplicate parents
        RETURN parent.text AS text, score, properties(node) AS metadata
        ORDER BY score DESC
        LIMIT $maxResults""";

private static final String DEFAULT_PARENT_QUERY = """
        UNWIND $rows AS row
        MATCH (p:SummaryChunk {parentId: $parentId})
        CREATE (p)-[:HAS_SUMMARY]->(u:%1$s {%2$s: row.%2$s})
        SET u += row.%3$s
        WITH row, u
        CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
        RETURN count(*)""";

EmbeddingStore defaultEmbeddingStore = Neo4jEmbeddingStore.builder()
    .driver(driver)
    .retrievalQuery(DEFAULT_RETRIEVAL)
    .entityCreationQuery(DEFAULT_PARENT_QUERY)
    .label("Summary")
    .indexName("summary_embedding_index")
    .dimension(384)
    .build();
```

#### HypotheticalQuestionGraphIngestor

To implement the [Hypothetical Question Retriever concept](https://graphrag.com/reference/graphrag/hypothetical-question-retriever/) by generating and embedding hypothetical questions derived from content chunks. This improves semantic search accuracy, especially for indirect or abstract user questions.
It enhances retrieval when queries don’t directly match document phrasing.


Example usage:
```java
HypotheticalQuestionGraphIngestor ingestor = HypotheticalQuestionGraphIngestor.builder()
        .embeddingModel(embeddingModel)
        .driver(driver)
        .documentSplitter(splitter)
        .questionModel(chatModel)
        .embeddingStore(embeddingStore)
        .build();
```

Unlike `Neo4jEmbeddingStoreIngestor`, it has the following default values:

- `query`: `"CREATE (:QuestionChunk $metadata)"`
- `systemPrompt`:
```text
You are generating hypothetical questions based on the information found in the text.
Make sure to provide full context in the generated questions.
```

- `userPrompt`:
```text
Use the given format to generate hypothetical questions from the following input:
{{input}}

Hypothetical questions:
```

- `embeddingStore`:
```java
private static final String DEFAULT_RETRIEVAL = """
        MATCH (node)<-[:HAS_QUESTION]-(parent)
        WITH parent, max(score) AS score, node // deduplicate parents
        RETURN parent.text AS text, score, properties(node) AS metadata
        ORDER BY score DESC
        LIMIT $maxResults""";

private static final String DEFAULT_PARENT_QUERY = """
        UNWIND $rows AS question
        MATCH (p:QuestionChunk {parentId: $parentId})
        WITH p, question
        CREATE (q:%1$s {%2$s: question.%2$s})
        SET q += question.%3$s
        MERGE (q)<-[:HAS_QUESTION]-(p)
        WITH q, question
        CALL db.create.setNodeVectorProperty(q, $embeddingProperty, question.%4$s)
        RETURN count(*)""";

EmbeddingStore defaultEmbeddingStore = Neo4jEmbeddingStore.builder()
    .driver(driver)
    .retrievalQuery(DEFAULT_RETRIEVAL_QUERY)
    .entityCreationQuery(DEFAULT_PARENT_QUERY)
    .label("Child")
    .indexName("child_embedding_index")
    .dimension(384)
    .build();
```

#### ParentChildGraphIngestor

To implement the [Parent-Child Retriever concept](https://graphrag.com/reference/graphrag/parent-child-retriever/).
It's useful where semantic search is done on child nodes but results are anchored to parent documents.
This ingestor stores child chunks with embeddings and, by default, links them to parent nodes using `:HAS_CHILD` relationships. Ideal for retrieving relevant fragments while referencing the broader document context.


```java
ParentChildGraphIngestor ingestor = ParentChildGraphIngestor.builder()
        .embeddingModel(embeddingModel)
        .driver(driver)
        .documentSplitter(parentSplitter)
        .documentChildSplitter(childSplitter)
        .build();
```

Unlike `Neo4jEmbeddingStoreIngestor`, it has the following default values:

- `query`: `"CREATE (:ParentChunk $metadata)"`

- `embeddingStore`:
```java
private static final String DEFAULT_RETRIEVAL = """
        MATCH (node)<-[:HAS_CHILD]-(parent)
        WITH parent, collect(node.text) AS chunks, max(score) AS score
        RETURN parent.text + reduce(r = "", c in chunks | r + "\n\n" + c) AS text,
               score,
               properties(parent) AS metadata
        ORDER BY score DESC
        LIMIT $maxResults""";

private static final String DEFAULT_PARENT_QUERY = """
        UNWIND $rows AS row
        MATCH (p:ParentChunk {parentId: $parentId})
        CREATE (p)-[:HAS_CHILD]->(u:%1$s {%2$s: row.%2$s})
        SET u += row.%3$s
        WITH row, u
        CALL db.create.setNodeVectorProperty(u, $embeddingProperty, row.%4$s)
        RETURN count(*)""";

EmbeddingStore defaultEmbeddingStore = Neo4jEmbeddingStore.builder()
        .driver(driver)
        .retrievalQuery(DEFAULT_RETRIEVAL)
        .entityCreationQuery(DEFAULT_PARENT_QUERY)
        .label("Child")
        .indexName("child_embedding_index")
        .dimension(384)
        .build();
```



### Neo4jChatMemoryStore

`Neo4jChatMemoryStore` is a specialized chat memory implementation that stores and retrieves conversational messages in a Neo4j graph database. It supports managing chat history with efficient querying and persistence using Neo4j nodes and relationships.

Here is how to create a `Neo4jChatMemoryStore` instance:

```java
Neo4jChatMemoryStore chatMemoryStore = Neo4jChatMemoryStore.builder()
    .<builderParameters>
    .build();
```

Where `<builderParameters>` includes `driver` as required, and optional properties for label and node property names.

Here is the complete builder list:

| Key                      | Default Value      | Description                                                                                                                    |
| ------------------------ | ------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| `driver`                 | *Required*         | The [Neo4j Java Driver instance](https://neo4j.com/docs/api/java-driver/current/org.neo4j.driver/org/neo4j/driver/Driver.html) |
| `label`                  | `"ChatMessage"`    | The label used for chat message nodes in Neo4j                                                                                 |
| `idProperty`             | `"id"`             | The property name for the message ID                                                                                           |
| `conversationIdProperty` | `"conversationId"` | The property name identifying the conversation                                                                                 |
| `timestampProperty`      | `"timestamp"`      | The property name for message timestamps                                                                                       |

#### Examples

**Basic usage with required parameter:**

```java
Neo4jChatMemoryStore chatMemoryStore = Neo4jChatMemoryStore.builder()
    .driver(neo4jDriver)
    .build();
```

**Customizing node label and properties:**

```java
Neo4jChatMemoryStore chatMemoryStore = Neo4jChatMemoryStore.builder()
    .driver(neo4jDriver)
    .label("Message")
    .idProperty("messageId")
    .conversationIdProperty("convId")
    .timestampProperty("timeSent")
    .build();
```









### Simple Flow Examples
The following are a few examples of the use flow for the `Neo4jEmbeddingStore` and `Neo4jText2CypherRetriever` APIs.
- `Neo4jEmbeddingStore`:
```java
private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

public static void minimalEmbedding() {
    try (Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.26")) {
        neo4j.start();

        EmbeddingStore<TextSegment> minimalEmbedding = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4j.getBoltUrl(), "neo4j", neo4j.getAdminPassword())
                .dimension(384)
                .build();


        TextSegment segment1 = TextSegment.from("I like football.", Metadata.from("test-key-1", "test-value-1"));
        Embedding embedding1 = embeddingModel.embed(segment1).content();

        TextSegment segment2 = TextSegment.from("The weather is good today.", Metadata.from("test-key-2", "test-value-2"));
        Embedding embedding2 = embeddingModel.embed(segment2).content();

        TextSegment segment3 = TextSegment.from("I like basketball.", Metadata.from("test-key-3", "test-value-3"));
        Embedding embedding3 = embeddingModel.embed(segment3).content();
        minimalEmbedding.addAll(
                List.of(embedding1, embedding2, embedding3),
                List.of(segment1, segment2, segment3)
        );

        Embedding queryEmbedding = embeddingModel.embed("What are your favourite sports?").content();
        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(2)
                .minScore(0.15)
                .build();
        List<EmbeddingMatch<TextSegment>> relevant = minimalEmbedding.search(request).matches();
        relevant.forEach(match -> {
            System.out.println(match.score()); // 0.8144289255142212
            System.out.println(match.embedded().text()); // I like football. || I like basketball.
        });
    }
}

public static void customEmbeddingStore() {
    try (Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.26")) {
        neo4j.start();
        
        Neo4jEmbeddingStore customEmbeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4j.getBoltUrl(), "neo4j", neo4j.getAdminPassword())
                .dimension(384)
                .indexName("customidx")
                .label("CustomLabel")
                .embeddingProperty("customProp")
                .idProperty("customId")
                .textProperty("customText")
                .build();
        
        TextSegment segment1 = TextSegment.from("I like football.");
        Embedding embedding1 = embeddingModel.embed(segment1).content();
        customEmbeddingStore.add(embedding1, segment1);

        TextSegment segment2 = TextSegment.from("The weather is good today.");
        Embedding embedding2 = embeddingModel.embed(segment2).content();
        customEmbeddingStore.add(embedding2, segment2);

        Embedding queryEmbedding = embeddingModel.embed("What is your favourite sport?").content();
        final EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(1)
                .build();
        List<EmbeddingMatch<TextSegment>> relevant = customEmbeddingStore.search(request).matches();
        EmbeddingMatch<TextSegment> embeddingMatch = relevant.get(0);

        System.out.println(embeddingMatch.score()); // 0.8144289255142212
        System.out.println(embeddingMatch.embedded().text()); // I like football.
    }
}
```
- `Neo4jText2CypherRetriever`:
```java
    private final ChatLanguageModel chatLanguageModel;

    public void Neo4jText2CypherRetriever() {
        try (
                Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5.16.0")
                                                        .withoutAuthentication()
                                                        .withLabsPlugins("apoc")
        ) {
            neo4jContainer.start();
            try (Driver driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none())) {
                try (Neo4jGraph graph = Neo4jGraph.builder().driver(driver).build()) {
                    try (Session session = driver.session()) {
                        session.run("CREATE (book:Book {title: 'Dune'})<-[:WROTE]-(author:Person {name: 'Frank Herbert'})");
                    }
                    graph.refreshSchema();
                    
                    Neo4jText2CypherRetriever retriever = Neo4jText2CypherRetriever.builder()
                            .graph(graph)
                            .chatLanguageModel(chatLanguageModel)
                            .build();

                    Query query = new Query("Who is the author of the book 'Dune'?");

                    List<Content> contents = retriever.retrieve(query);

                    System.out.println(contents.get(0).textSegment().text()); // "Frank Herbert"
                }
            }
        }
    }
```
[Examples Sources](https://github.com/langchain4j/langchain4j-examples/tree/main/neo4j-example/src/main/java)
