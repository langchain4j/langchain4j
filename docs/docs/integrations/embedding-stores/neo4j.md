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

<!-- if we want to use the SpringBoot starter -->
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

## Usage Examples


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

---





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

Here is how to create a `Neo4jText2CypherRetriever` instance:

```java
Neo4jText2CypherRetriever retriever = Neo4jText2CypherRetriever.builder().<builderParameters>.build();
````

Here is the complete builder list:

| Key | Default Value     | Description |
| ---------- | ----------------- | ---------- |
| `graph`    | *Required*        | See below  |
| `chatModel` | *Required*        | The [ChatModel](https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/model/chat/ChatModel.java) implementation used to create the Cypher query from a natural language question |
| `prompt`   | See example below | The prompt that will be used with the chatModel |
| `examples` | Empty string      | Additional examples to enrich and improve the result |

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
To create a **SpringBoot starter**, the Neo4j starter provides at the time being the following `application.properties`:
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
Configuring the Starter allows us to create a simple SpringBoot project like the following:
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
# and store it with a label "SpringBoot"
curl -X POST localhost:8083/api/embeddings/add -H "Content-Type: text/plain" -d "embeddingTest"

# to search the first 5 embeddings
curl -X POST localhost:8083/api/embeddings/search -H "Content-Type: text/plain" -d "querySearchTest"
```
To create `Neo4jText2CypherRetriever` instance, you can execute with some Cypher examples:
```java
Neo4jGraph graphStreamer = Neo4jGraph.builder().driver(driver).build();
List<String> examples = List.of(
        """
    # Which streamer has the most followers?
    MATCH (s:Stream)
    RETURN s.name AS streamer
    ORDER BY s.followers DESC LIMIT 1
    """,
        """
    # How many streamers are from Norway?
    MATCH (s:Stream)-[:HAS_LANGUAGE]->(:Language {{name: 'Norwegian'}})
    RETURN count(s) AS streamers
    Note: Do not include any explanations or apologies in your responses.
    Do not respond to any questions that might ask anything else than for you to construct a Cypher statement.
    Do not include any text except the generated Cypher statement.
    """);
final String textQuery = "Which streamer from Italy has the most followers?";
Query query = new Query(textQuery);

Neo4jText2CypherRetriever neo4jContentRetrieverWithoutExample = Neo4jText2CypherRetriever.builder()
        .graph(graphStreamer)
        .chatLanguageModel(openAiChatModel)
        .build();
// empty results
List<Content> contentsWithoutExample = neo4jContentRetrieverWithoutExample.retrieve(query);

Neo4jText2CypherRetriever neo4jContentRetriever = Neo4jText2CypherRetriever.builder()
        .graph(graphStreamer)
        .chatLanguageModel(openAiChatModel)
        .examples(examples)
        .build();

final String text = RetryUtils.withRetry(
        () -> {
            List<Content> contents = neo4jContentRetriever.retrieve(query);
            assertThat(contents).hasSize(1);
            return contents.get(0).textSegment().text();
        },
        5);

final String name = driver.session()
        .run("MATCH (s:Stream)-[:HAS_LANGUAGE]->(l:Language {name: 'Italian'}) RETURN s.name ORDER BY s.followers DESC LIMIT 1")
        .single()
        .values()
        .get(0)
        .toString();
System.out.println(name); // Nino Frassica.
```

Moreover, we can enrich and improve the result by just adding few-shot examples to prompt.
```java
Neo4jGraph neo4jGraph = /* Neo4jGraph instance */

List<String> examples = List.of(
    """
    # Which streamer has the most followers?
    MATCH (s:Stream)
    RETURN s.name AS streamer
    ORDER BY s.followers DESC LIMIT 1
    """,
    """
    # How many streamers are from Norway?
    MATCH (s:Stream)-[:HAS_LANGUAGE]->(:Language {{name: 'Norwegian'}})
    RETURN count(s) AS streamers
    """);

Neo4jText2CypherRetriever neo4jContentRetriever = Neo4jText2CypherRetriever.builder()
        .graph(neo4jGraph)
        .chatLanguageModel(openAiChatModel)
        // add the above examples
        .examples(examples)
        .build();

// retrieve the optimized results
final String textQuery = "Which streamer from Italy has the most followers?";
Query query = new Query(textQuery);
List<Content> contents = neo4jContentRetriever.retrieve(query);

System.out.println(contents.get(0).textSegment().text());
// output: "The most followed italian streamer"
```


### Simple Flow Examples
The following are a few examples of the use flow for the two APIs.
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
