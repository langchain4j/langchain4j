package dev.langchain4j.store.embedding.typed;

import dev.langchain4j.store.embedding.hibernate.EmbeddedText;
import dev.langchain4j.store.embedding.hibernate.Embedding;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import dev.langchain4j.store.embedding.hibernate.MetadataAttribute;
import dev.langchain4j.store.embedding.hibernate.UnmappedMetadata;
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

        @EmbeddedText
        private String text;

        @Embedding
        @Array(length = 384)
        private float[] embedding;

        @UnmappedMetadata
        private Map<String, Object> metadata;

        @MetadataAttribute
        private String key;

        @MetadataAttribute
        private String name;

        @MetadataAttribute
        private Float age;

        @MetadataAttribute
        private String city;

        @MetadataAttribute
        private String country;

        @MetadataAttribute
        private String string_empty;

        @MetadataAttribute
        private String string_space;

        @MetadataAttribute
        private String string_abc;

        @MetadataAttribute
        private UUID uuid;

        @MetadataAttribute
        private Integer integer_min;

        @MetadataAttribute
        private Integer integer_minus_1;

        @MetadataAttribute
        private Integer integer_0;

        @MetadataAttribute
        private Integer integer_1;

        @MetadataAttribute
        private Integer integer_max;

        @MetadataAttribute
        private Long long_min;

        @MetadataAttribute
        private Long long_minus_1;

        @MetadataAttribute
        private Long long_0;

        @MetadataAttribute
        private Long long_1;

        @MetadataAttribute
        private Long long_1746714878034235396;

        @MetadataAttribute
        private Long long_max;

        @MetadataAttribute
        private Float float_min;

        @MetadataAttribute
        private Float float_minus_1;

        @MetadataAttribute
        private Float float_0;

        @MetadataAttribute
        private Float float_1;

        @MetadataAttribute
        private Float float_123;

        @MetadataAttribute
        private Float float_max;

        @MetadataAttribute
        private Double double_minus_1;

        @MetadataAttribute
        private Double double_0;

        @MetadataAttribute
        private Double double_1;

        @MetadataAttribute
        private Double double_123;
    }
}
