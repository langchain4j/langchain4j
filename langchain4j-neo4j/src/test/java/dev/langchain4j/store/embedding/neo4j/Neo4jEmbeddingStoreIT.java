package dev.langchain4j.store.embedding.neo4j;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.selenium.SeleniumDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.transformer.jsoup.HtmlToTextDocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_EMBEDDING_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_ID_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_TEXT_PROP;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.data.Percentage.withPercentage;

@Testcontainers
class Neo4jEmbeddingStoreIT {

    public static final String USERNAME = "neo4j";
    public static final String ADMIN_PASSWORD = "adminPass";
    public static final String LABEL_TO_SANITIZE = "Label ` to \\ sanitize";

    @Container
    static Neo4jContainer<?> neo4jContainer =
            new Neo4jContainer<>(DockerImageName.parse("neo4j:5.14.0")).withAdminPassword(ADMIN_PASSWORD);

    private static final String METADATA_KEY = "test-key";

    private EmbeddingStore<TextSegment> embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static Session session;

    @BeforeAll
    static void startContainer() {
        neo4jContainer.start();
        Driver driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic(USERNAME, ADMIN_PASSWORD));
        session = driver.session();
    }

    @BeforeEach
    void initEmptyNeo4jEmbeddingStore() {
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(LABEL_TO_SANITIZE)
                .build();
    }

    @AfterEach
    void afterEach() {
        session.run("MATCH (n) DETACH DELETE n");
        String indexName = ((Neo4jEmbeddingStore) embeddingStore).getIndexName();
        session.run("DROP INDEX " + SchemaNames.sanitize(indexName).get());
    }

    @Test
    void should_add_embedding() {
        Embedding embedding = embeddingModel.embed("embedText").content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();

        checkEntitiesCreated(relevant.size(), iterator -> checkDefaultProps(embedding, match, iterator.next()));
    }

    @Test
    void should_throws_error_if_full_text_retrieval_is_invalid() {
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .fullTextIndexName("full_text_with_invalid_retrieval")
                .fullTextQuery("Matrix")
                .fullTextAutocreate(true)
                .fullTextRetrievalQuery("RETURN properties(invalid) AS metadata")
                .label(LABEL_TO_SANITIZE)
                .build();

        List<Embedding> embeddings =
                embeddingModel.embedAll(List.of(TextSegment.from("test"))).content();
        embeddingStore.addAll(embeddings);

        final Embedding queryEmbedding = embeddingModel.embed("Matrix").content();

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        try {
            embeddingStore.search(embeddingSearchRequest).matches();
            fail("should fail due to not existent index");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("Variable `invalid` not defined");
        }
    }

    @Test
    void row_batches_20000_elements_and_full_text() {
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label("labelBatch")
                .indexName("movie_vector")
                .fullTextIndexName("fullTextIndexNameBatch")
                .fullTextQuery("fullTextSearchBatch")
                .build();
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(20000, embeddingStore);
        assertThat(rowsBatched).hasSize(2);
        assertThat(rowsBatched.get(0)).hasSize(10000);
        assertThat(rowsBatched.get(1)).hasSize(10000);
    }

    @Test
    void should_throws_error_if_fulltext_doesnt_exist() {
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .fullTextIndexName("movie_text_non_existent")
                .fullTextQuery("Matrix")
                .label(LABEL_TO_SANITIZE)
                .build();

        List<Embedding> embeddings =
                embeddingModel.embedAll(List.of(TextSegment.from("test"))).content();
        embeddingStore.addAll(embeddings);

        final Embedding queryEmbedding = embeddingModel.embed("Matrix").content();

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        try {
            embeddingStore.search(embeddingSearchRequest).matches();
            fail("should fail due to not existent index");
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("There is no such fulltext schema index");
        }
    }

    @Test
    void should_get_embeddings_if_autocreate_full_text_is_true() {
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)

                .fullTextIndexName("movie_text")
                .fullTextQuery("Matrix")
                .fullTextAutocreate(true)
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
        assertThat(matches).hasSize(1);
    }

    @Test
    void should_add_embedding_with_id() {

        final String fullTextIndexName = "movie_text";
        final String label = "Movie";
        final String fullTextSearch = "Matrix";
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(label)
                .indexName("movie_vector")
                .fullTextIndexName(fullTextIndexName)
                .fullTextQuery(fullTextSearch)
                .build();

        final List<String> texts = List.of(
                "The Matrix: Welcome to the Real World",
                "The Matrix Reloaded: Free your mind",
                "The Matrix Revolutions: Everything that has a beginning has an end",
                "The Devil's Advocate: Evil has its winning ways",
                "A Few Good Men: In the heart of the nation's capital, in a courthouse of the U.S. government, one man will stop at nothing to keep his honor, and one will stop at nothing to find the truth.",
                "Top Gun: I feel the need, the need for speed.",
                "Jerry Maguire: The rest of his life begins now.",
                "Stand By Me: For some, it's the last real taste of innocence, and the first real taste of life. But for everyone, it's the time that memories are made of.",
                "As Good as It Gets: A comedy from the heart that goes for the throat.");

        final List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        final Embedding queryEmbedding = embeddingModel.embed(fullTextSearch).content();

        session.executeWrite(tx -> {
            final String query = "CREATE FULLTEXT INDEX %s IF NOT EXISTS FOR (e:%s) ON EACH [e.%s]"
                    .formatted(fullTextIndexName, label, DEFAULT_ID_PROP);
            tx.run(query).consume();
            return null;
        });

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        final List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(embeddingSearchRequest).matches();
        assertThat(matches).hasSize(3);
        matches.forEach(i -> {
            final String embeddedText = i.embedded().text();
            assertThat(embeddedText).contains(fullTextSearch);
        });

        Neo4jEmbeddingStore embeddingStoreWithoutFullText = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(label)
                .indexName("movie_vector")
                .build();

        embeddingStoreWithoutFullText.addAll(embeddings, segments);
        final List<EmbeddingMatch<TextSegment>> matchesWithoutFullText = embeddingStore.search(embeddingSearchRequest)
                .matches();
        assertThat(matchesWithoutFullText).hasSize(3);
        matchesWithoutFullText.forEach(i -> {
            final String embeddedText = i.embedded().text();
            assertThat(embeddedText).contains(fullTextSearch);
        });
    }

    // Emulating as far as possible the langchain (python) use case
    // https://neo4j.com/developer-blog/enhance-rag-knowledge-graph/
    @Test
    void should_emulate_issue_1306_case() {

        final String label = "Elisabeth";
        Neo4jEmbeddingStore embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .label(label)
                .indexName("elisabeth_vector")
                .fullTextIndexName("elisabeth_text")
                .fullTextQuery("Matrix")
                .build();

        DocumentParser parser = new TextDocumentParser();
        HtmlToTextDocumentTransformer extractor = new HtmlToTextDocumentTransformer();
        BrowserWebDriverContainer<?> chromeContainer = new BrowserWebDriverContainer<>()
                .withCapabilities(new ChromeOptions());
        chromeContainer.start();
        RemoteWebDriver webDriver = new RemoteWebDriver(chromeContainer.getSeleniumAddress(), new ChromeOptions());
        SeleniumDocumentLoader loader = SeleniumDocumentLoader.builder()
                .webDriver(webDriver)
                .timeout(Duration.ofSeconds(30))
                .build();
        String url = "https://en.wikipedia.org/wiki/Elizabeth_I";
        Document document = loader.load(url, parser);
        Document textDocument = extractor.transform(document);

        session.executeWrite(tx -> {
            final String s = "CREATE FULLTEXT INDEX elisabeth_text IF NOT EXISTS FOR (e:%s) ON EACH [e.%s]"
                    .formatted(label, DEFAULT_ID_PROP);
            tx.run(s).consume();
            return null;
        });

        final List<TextSegment> split = new DocumentByParagraphSplitter(20, 10).split(textDocument);

        List<Embedding> embeddings = embeddingModel.embedAll(split).content();
        embeddingStore.addAll(embeddings, split);

        final Embedding queryEmbedding = embeddingModel.embed("Elisabeth I").content();

        final EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(3)
                .build();
        final List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(embeddingSearchRequest).matches();
        matches.forEach(i -> {
            final String embeddedText = i.embedded().text();
            assertThat(embeddedText).contains("Elizabeth");
        });

        String wikiContent = textDocument.text().split("Signature ")[1];
        wikiContent = wikiContent.substring(0, 5000);
        
        final String userMessage = String.format("""
                        Can you transform the following text into Cypher statements using both nodes and relationships?
                        Each node and relation should have a single property "id",\s
                        and each node has an additional label named __Entity__
                        The id property values should have whitespace instead of _ or other special characters.
                                        
                        Just returns an unique query non ; separated,
                        without the ``` wrapping.
                                        
                        ```
                        %s
                        ```
                        """,
                wikiContent
        );

        final OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
                .apiKey("demo")
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();
        final String generate = openAiChatModel.generate(userMessage);

        for (String query: generate.split(";")) {
            session.executeWrite(tx -> {
                tx.run(query).consume();
                return null;
            });
        }

        final List<EmbeddingMatch<TextSegment>> matchesWithFullText =
                embeddingStore.search(embeddingSearchRequest).matches();
        assertThat(matchesWithFullText).hasSize(3);
        matchesWithFullText.forEach(i -> {
            final String embeddedText = i.embedded().text();
            assertThat(embeddedText).contains("Elizabeth");
        });
    }

    @Test
    void should_add_embedding_with_segment() {

        TextSegment segment = TextSegment.from(randomUUID());
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);

        checkEntitiesCreated(relevant.size(), iterator -> {
            List<String> otherProps = Collections.singletonList(DEFAULT_TEXT_PROP);
            checkDefaultProps(embedding, match, iterator.next(), otherProps);
        });
    }

    @Test
    void should_add_embedding_with_segment_with_metadata() {
        checkSegmentWithMetadata(METADATA_KEY, LABEL_TO_SANITIZE);
    }

    @Test
    void should_add_embedding_with_segment_with_custom_metadata_prefix() {
        String metadataPrefix = "metadata.";
        String labelName = "CustomLabelName";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label(labelName)
                .indexName("customIdxName")
                .build();

        String metadataCompleteKey = metadataPrefix + METADATA_KEY;

        checkSegmentWithMetadata(metadataCompleteKey, labelName);
    }

    @Test
    void should_retrieve_custom_metadata_with_match() {
        String metadataPrefix = "metadata.";
        String labelName = "CustomLabelName";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label(labelName)
                .indexName("customIdxName")
                .retrievalQuery(
                        "RETURN {foo: 'bar'} AS metadata, node.text AS text, node.embedding AS embedding, node.id AS id, score")
                .build();

        String text = randomUUID();
        TextSegment segment = TextSegment.from(text, Metadata.from(METADATA_KEY, "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);

        TextSegment customMeta = TextSegment.from(text, Metadata.from("foo", "bar"));
        assertThat(match.embedded()).isEqualTo(customMeta);

        checkEntitiesCreated(relevant.size(), labelName, iterator -> {
            List<String> otherProps = Arrays.asList(DEFAULT_TEXT_PROP, metadataPrefix + METADATA_KEY);
            checkDefaultProps(embedding, DEFAULT_ID_PROP, match, iterator.next(), otherProps);
        });
    }

    @Test
    void should_add_embedding_with_segment_with_metadata_and_custom_id_prop() {
        String metadataPrefix = "metadata.";
        String customIdProp = "customId ` & Prop ` To Sanitize";

        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label("CustomLabelName")
                .indexName("customIdxName")
                .idProperty(customIdProp)
                .build();

        String metadataCompleteKey = metadataPrefix + METADATA_KEY;

        checkSegmentWithMetadata(metadataCompleteKey, customIdProp, "CustomLabelName");
    }

    @Test
    void should_add_multiple_embeddings() {
        Embedding firstEmbedding = embeddingModel.embed("firstEmbedText").content();
        Embedding secondEmbedding = embeddingModel.embed("secondEmbedText").content();

        List<String> ids = embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));
        assertThat(ids).hasSize(2);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isNull();

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(secondMatch.embedding()).isEqualTo(secondEmbedding);
        assertThat(secondMatch.embedded()).isNull();

        checkEntitiesCreated(relevant.size(), iterator -> {
            iterator.forEachRemaining(node -> {
                if (node.get(DEFAULT_ID_PROP).asString().equals(firstMatch.embeddingId())) {
                    checkDefaultProps(firstEmbedding, firstMatch, node);
                } else {
                    checkDefaultProps(secondEmbedding, secondMatch, node);
                }
            });
        });
    }

    @Test
    void should_add_multiple_embeddings_with_segments() {

        TextSegment firstSegment = TextSegment.from("firstText");
        Embedding firstEmbedding = embeddingModel.embed(firstSegment.text()).content();
        TextSegment secondSegment = TextSegment.from("secondText");
        Embedding secondEmbedding = embeddingModel.embed(secondSegment.text()).content();

        List<String> ids =
                embeddingStore.addAll(asList(firstEmbedding, secondEmbedding), asList(firstSegment, secondSegment));
        assertThat(ids).hasSize(2);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isEqualTo(firstSegment);

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(secondMatch.embedding()).isEqualTo(secondEmbedding);
        assertThat(secondMatch.embedded()).isEqualTo(secondSegment);

        checkEntitiesCreated(relevant.size(), iterator -> {
            List<String> otherProps = Collections.singletonList(DEFAULT_TEXT_PROP);
            iterator.forEachRemaining(node -> {
                if (node.get(DEFAULT_ID_PROP).asString().equals(firstMatch.embeddingId())) {
                    checkDefaultProps(firstEmbedding, firstMatch, node, otherProps);
                } else {
                    checkDefaultProps(secondEmbedding, secondMatch, node, otherProps);
                }
            });
        });
    }

    @Test
    void should_find_with_min_score() {

        String firstId = randomUUID();
        Embedding firstEmbedding = embeddingModel.embed("firstEmbedText").content();
        embeddingStore.add(firstId, firstEmbedding);

        String secondId = randomUUID();
        Embedding secondEmbedding = embeddingModel.embed("secondEmbedText").content();
        embeddingStore.add(secondId, secondEmbedding);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(firstId);
        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant2 =
                embeddingStore.findRelevant(firstEmbedding, 10, secondMatch.score() - 0.01);
        assertThat(relevant2).hasSize(2);
        assertThat(relevant2.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant2.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant3 =
                embeddingStore.findRelevant(firstEmbedding, 10, secondMatch.score());
        assertThat(relevant3).hasSize(2);
        assertThat(relevant3.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant3.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant4 =
                embeddingStore.findRelevant(firstEmbedding, 10, secondMatch.score() + 0.01);
        assertThat(relevant4).hasSize(1);
        assertThat(relevant4.get(0).embeddingId()).isEqualTo(firstId);

        checkEntitiesCreated(relevant.size(), iterator -> {
            iterator.forEachRemaining(node -> {
                if (node.get(DEFAULT_ID_PROP).asString().equals(firstMatch.embeddingId())) {
                    checkDefaultProps(firstEmbedding, firstMatch, node);
                } else {
                    checkDefaultProps(secondEmbedding, secondMatch, node);
                }
            });
        });
    }

    @Test
    void should_return_correct_score() {

        Embedding embedding = embeddingModel.embed("hello").content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        Embedding referenceEmbedding = embeddingModel.embed("hi").content();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(referenceEmbedding, 1);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score())
                .isCloseTo(
                        RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                        withPercentage(1));

        checkEntitiesCreated(relevant.size(), iterator -> checkDefaultProps(embedding, match, iterator.next()));
    }

    @Test
    void should_throw_error_if_another_index_name_with_different_label_exists() {
        String metadataPrefix = "metadata.";
        String idxName = "WillFail";

        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .indexName(idxName)
                .metadataPrefix(metadataPrefix)
                .awaitIndexTimeout(20)
                .build();

        String secondLabel = "Second label";
        try {
            embeddingStore = Neo4jEmbeddingStore.builder()
                    .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                    .dimension(384)
                    .label(secondLabel)
                    .indexName(idxName)
                    .metadataPrefix(metadataPrefix)
                    .build();
            fail("Should fail due to idx conflict");
        } catch (RuntimeException e) {
            String errMsg = String.format(
                    "It's not possible to create an index for the label `%s` and the property `%s`",
                    secondLabel, DEFAULT_EMBEDDING_PROP);
            assertThat(e.getMessage()).contains(errMsg);
        }
    }

    @Test
    void row_batches_single_element() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(1);
        assertThat(rowsBatched).hasSize(1);
        assertThat(rowsBatched.get(0)).hasSize(1);
    }

    @Test
    void row_batches_10000_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(10000);
        assertThat(rowsBatched).hasSize(1);
        assertThat(rowsBatched.get(0)).hasSize(10000);
    }

    @Test
    void row_batches_20000_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(20000);
        assertThat(rowsBatched).hasSize(2);
        assertThat(rowsBatched.get(0)).hasSize(10000);
        assertThat(rowsBatched.get(1)).hasSize(10000);
    }

    @Test
    void row_batches_11001_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(11001);
        assertThat(rowsBatched).hasSize(2);
        assertThat(rowsBatched.get(0)).hasSize(10000);
        assertThat(rowsBatched.get(1)).hasSize(1001);
    }

    private List<List<Map<String, Object>>> getListRowsBatched(int numElements) {
        return getListRowsBatched(numElements, (Neo4jEmbeddingStore) embeddingStore);
    }

    private List<List<Map<String, Object>>> getListRowsBatched(int numElements, Neo4jEmbeddingStore embeddingStore) {
        List<TextSegment> embedded = IntStream.range(0, numElements)
                .mapToObj(i -> TextSegment.from("text-" + i))
                .toList();
        List<String> ids =
                IntStream.range(0, numElements).mapToObj(i -> "id-" + i).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(embedded).content();

        return Neo4jEmbeddingUtils.getRowsBatched(embeddingStore, ids, embeddings, embedded)
                .toList();
    }

    private void checkSegmentWithMetadata(String metadataKey, String labelName) {
        checkSegmentWithMetadata(metadataKey, DEFAULT_ID_PROP, labelName);
    }

    private void checkSegmentWithMetadata(String metadataKey, String idProp, String labelName) {
        TextSegment segment = TextSegment.from(randomUUID(), Metadata.from(METADATA_KEY, "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);

        checkEntitiesCreated(relevant.size(), labelName, iterator -> {
            List<String> otherProps = Arrays.asList(DEFAULT_TEXT_PROP, metadataKey);
            checkDefaultProps(embedding, idProp, match, iterator.next(), otherProps);
        });
    }

    private void checkEntitiesCreated(int expectedSize, Consumer<Iterator<Node>> nodeConsumer) {
        checkEntitiesCreated(expectedSize, LABEL_TO_SANITIZE, nodeConsumer);
    }

    private void checkEntitiesCreated(int expectedSize, String labelName, Consumer<Iterator<Node>> nodeConsumer) {
        String query = "MATCH (n:%s) RETURN n ORDER BY n.%s"
                .formatted(SchemaNames.sanitize(labelName).get(), DEFAULT_TEXT_PROP);

        List<Node> n = session.run(query).list(i -> i.get("n").asNode());

        assertThat(n).hasSize(expectedSize);

        Iterator<Node> iterator = n.iterator();
        nodeConsumer.accept(iterator);

        assertThat(iterator).isExhausted();
    }

    private void checkDefaultProps(Embedding embedding, EmbeddingMatch<TextSegment> match, Node node) {
        checkDefaultProps(embedding, DEFAULT_ID_PROP, match, node, Collections.emptyList());
    }

    private void checkDefaultProps(
            Embedding embedding, EmbeddingMatch<TextSegment> match, Node node, List<String> otherProps) {
        checkDefaultProps(embedding, DEFAULT_ID_PROP, match, node, otherProps);
    }

    private void checkDefaultProps(
            Embedding embedding, String idProp, EmbeddingMatch<TextSegment> match, Node node, List<String> otherProps) {
        checkPropKeys(node, idProp, otherProps);

        assertThat(node.get(idProp).asString()).isEqualTo(match.embeddingId());

        List<Float> floats = node.get(DEFAULT_EMBEDDING_PROP).asList(Value::asFloat);
        assertThat(floats).isEqualTo(embedding.vectorAsList());
    }

    private void checkPropKeys(Node node, String idProp, List<String> otherProps) {
        List<String> strings = new ArrayList<>();
        // default props
        strings.add(idProp);
        strings.add(DEFAULT_EMBEDDING_PROP);
        // other props
        strings.addAll(otherProps);

        assertThat(node.keys()).containsExactlyInAnyOrderElementsOf(strings);
    }
}
