package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreWithJSONBFilteringIT extends PgVectorEmbeddingStoreConfigIT {

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(GenericEmbeddingEntity.class)
                .setJdbcUrl(pgVector.getJdbcUrl())
                .setCredentials("test", "test")
                .setSchemaExportAction(Action.CREATE_DROP)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE, SourceType.SCRIPT_THEN_METADATA)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, "/setup.sql")
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_LOAD_SCRIPT_SOURCE, "/import-with-gin.sql")
                .buildSessionFactory();
        embeddingStore = HibernateEmbeddingStore.builder(GenericEmbeddingEntity.class)
                .sessionFactory(sessionFactory)
                .build();
    }

    @Test
    @Disabled(
            "The test tries to assign an id explicitly, but the entity uses a generator that doesn't allow assignment")
    @Override
    protected void should_add_embedding_with_id() {
        super.should_add_embedding_with_id();
    }

    @Test
    @Disabled(
            "The test tries to assign an id explicitly, but the entity uses a generator that doesn't allow assignment")
    @Override
    protected void should_add_multiple_embeddings_with_ids_and_segments() {
        super.should_add_multiple_embeddings_with_ids_and_segments();
    }
}
