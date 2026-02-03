package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.store.embedding.hibernate.Embedding;
import dev.langchain4j.store.embedding.hibernate.EmbeddingText;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.Metadata;
import dev.langchain4j.store.embedding.hibernate.TextMetadata;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org.hibernate.tool.schema.Action;
import org.hibernate.tool.schema.SourceType;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreWithColumnsFilteringIT extends PgVectorEmbeddingStoreConfigIT {

    @BeforeAll
    static void setup() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(EntityWithColumns.class)
                .setJdbcUrl(pgVector.getJdbcUrl())
                .setCredentials("test", "test")
                .setSchemaExportAction(Action.CREATE_DROP)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SOURCE, SourceType.SCRIPT_THEN_METADATA)
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_CREATE_SCRIPT_SOURCE, "/setup.sql")
                .setProperty(SchemaToolingSettings.JAKARTA_HBM2DDL_LOAD_SCRIPT_SOURCE, "/import-with-multi.sql")
                .buildSessionFactory();
        embeddingStore = HibernateEmbeddingStore.builder(EntityWithColumns.class)
                .sessionFactory(sessionFactory)
                .build();
    }

    @Entity(name = "EntityWithColumns")
    @Table(name = "columns_embedding_entity")
    public static class EntityWithColumns {
        @Id
        private UUID id;

        @EmbeddingText
        private String text;

        @Embedding
        @Array(length = 384)
        private float[] embedding;

        @TextMetadata
        private Map<String, Object> metadata;

        @Metadata
        private String key;

        @Metadata
        private String name;

        @Metadata
        private Float age;

        @Metadata
        private String city;

        @Metadata
        private String country;

        @Metadata
        private String string_empty;

        @Metadata
        private String string_space;

        @Metadata
        private String string_abc;

        @Metadata
        private UUID uuid;

        @Metadata
        private Integer integer_min;

        @Metadata
        private Integer integer_minus_1;

        @Metadata
        private Integer integer_0;

        @Metadata
        private Integer integer_1;

        @Metadata
        private Integer integer_max;

        @Metadata
        private Long long_min;

        @Metadata
        private Long long_minus_1;

        @Metadata
        private Long long_0;

        @Metadata
        private Long long_1;

        @Metadata
        private Long long_1746714878034235396;

        @Metadata
        private Long long_max;

        @Metadata
        private Float float_min;

        @Metadata
        private Float float_minus_1;

        @Metadata
        private Float float_0;

        @Metadata
        private Float float_1;

        @Metadata
        private Float float_123;

        @Metadata
        private Float float_max;

        @Metadata
        private Double double_minus_1;

        @Metadata
        private Double double_0;

        @Metadata
        private Double double_1;

        @Metadata
        private Double double_123;
    }
}
