package dev.langchain4j.store.embedding.typed;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.query.restriction.Path;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreCustomEntityIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    static EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    static SessionFactory sessionFactory;

    private HibernateEmbeddingStore<BookEntity> embeddingStore;

    @Test
    protected void should_filter_by_embeddable_metadata() {
        Embedding embedding =
                embeddingModel.embed("Books by the author John Smith").content();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .minScore(0.75)
                .build());
        assertEquals(2, result.matches().size());
        assertEquals("1", result.matches().get(0).embeddingId());
        assertEquals("2", result.matches().get(1).embeddingId());

        result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .filter(MetadataFilterBuilder.metadataKey("details.language").isEqualTo("English"))
                .maxResults(10)
                .minScore(0.75)
                .build());

        assertEquals(1, result.matches().size());
        assertEquals("1", result.matches().get(0).embeddingId());
    }

    @Test
    protected void should_filter_by_association_metadata() {
        Embedding embedding =
                embeddingModel.embed("Books by the author John Smith").content();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .minScore(0.75)
                .build());
        assertEquals(2, result.matches().size());
        assertEquals("1", result.matches().get(0).embeddingId());
        assertEquals("2", result.matches().get(1).embeddingId());

        result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .filter(MetadataFilterBuilder.metadataKey("author.id").isEqualTo(2L))
                .maxResults(10)
                .minScore(0.75)
                .build());

        assertTrue(result.matches().isEmpty());
    }

    @Test
    protected void should_filter_by_embeddable_metadata_restriction() {
        Embedding embedding =
                embeddingModel.embed("Books by the author John Smith").content();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .minScore(0.75)
                .build());
        assertEquals(2, result.matches().size());
        assertEquals("1", result.matches().get(0).embeddingId());
        assertEquals("2", result.matches().get(1).embeddingId());

        result = embeddingStore.search(
                embedding,
                0.75,
                Path.from(BookEntity.class)
                        .to(BookEntity_.details)
                        .to(BookDetailsEmbeddable_.language)
                        .equalTo("English"),
                10);

        assertEquals(1, result.matches().size());
        assertEquals("1", result.matches().get(0).embeddingId());
    }

    @Test
    protected void should_filter_by_association_metadata_restriction() {
        Embedding embedding =
                embeddingModel.embed("Books by the author John Smith").content();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(10)
                .minScore(0.75)
                .build());
        assertEquals(2, result.matches().size());
        assertEquals("1", result.matches().get(0).embeddingId());
        assertEquals("2", result.matches().get(1).embeddingId());

        result = embeddingStore.search(
                embedding,
                0.75,
                Path.from(BookEntity.class)
                        .to(BookEntity_.author)
                        .to(AuthorEntity_.id)
                        .equalTo(2L),
                10);

        assertTrue(result.matches().isEmpty());
    }

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(BookEntity.class)
                .addAnnotatedClass(AuthorEntity.class)
                .setJdbcUrl(pgVector.getJdbcUrl())
                .setCredentials("test", "test")
                .setSchemaExportAction(Action.CREATE_DROP)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE, SourceType.SCRIPT_THEN_METADATA)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, "/setup.sql")
                .showSql(true, true, true)
                .buildSessionFactory();
        sessionFactory.inStatelessTransaction(session -> {
            AuthorEntity a1 = new AuthorEntity();
            a1.setFirstname("John");
            a1.setLastname("Smith");
            AuthorEntity a2 = new AuthorEntity();
            a2.setFirstname("Jim");
            a2.setLastname("Doe");
            session.insertMultiple(List.of(a1, a2));

            BookEntity b1 = new BookEntity();
            b1.setId(1L);
            b1.setAuthor(a1);
            b1.setTitle("Book 1");
            b1.setContent("This is a Book 1");
            b1.getDetails().setLanguage("English");
            b1.getDetails().setAbstractText("Some abstract text about Book 1");
            BookEntity b2 = new BookEntity();
            b2.setId(2L);
            b2.setAuthor(a1);
            b2.setTitle("Buch 2");
            b2.setContent("Das ist Buch 2");
            b2.getDetails().setLanguage("German");
            b2.getDetails().setAbstractText("Ein Auszug von Buch 2");
            BookEntity b3 = new BookEntity();
            b3.setId(3L);
            b3.setAuthor(a2);
            b3.setTitle("Book 3");
            b3.setContent("This is a Book 3");
            b3.getDetails().setLanguage("English");
            b3.getDetails().setAbstractText("Some abstract text about Book 3");
            session.insertMultiple(List.of(b1, b2, b3));
        });

        sessionFactory.inStatelessTransaction(session -> {
            List<BookEntity> books = session.createSelectionQuery(
                            "from BookEntity b join fetch b.author where b.embedding is null", BookEntity.class)
                    .getResultList();
            for (BookEntity book : books) {
                Response<Embedding> response = embeddingModel.embed(String.format(
                        "Title: %s\nAuthor: %s %s\nLanguage: %s\nAbstract: %s\nContent: %s",
                        book.getTitle(),
                        book.getAuthor().getFirstname(),
                        book.getAuthor().getLastname(),
                        book.getDetails().getLanguage(),
                        book.getDetails().getAbstractText(),
                        book.getContent()));
                book.setEmbedding(response.content().vector());
                book.setMetadata(response.metadata());
            }
            session.updateMultiple(books);
        });
    }

    @BeforeEach
    void beforeEach() {
        embeddingStore = HibernateEmbeddingStore.builder(BookEntity.class)
                .sessionFactory(sessionFactory)
                .build();
    }

    @AfterEach
    void afterEach() {
        embeddingStore = null;
    }

    @AfterAll
    static void tearDown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}
