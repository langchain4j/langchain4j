package dev.langchain4j.store.embedding.generic;

import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@Testcontainers
class OracleHibernateEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static OracleContainer databaseContainer = new OracleContainer("gvenzl/oracle-free:23.6-faststart");

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private HibernateEmbeddingStore<?> embeddingStore;

    @Override
    protected void ensureStoreIsReady() {
        embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
                .databaseKind(DatabaseKind.ORACLE)
                .host(databaseContainer.getHost())
                .port(databaseContainer.getFirstMappedPort())
                .database(databaseContainer.getDatabaseName())
                .user(databaseContainer.getUsername())
                .password(databaseContainer.getPassword())
                .table("test" + nextInt(1, 1000))
                .dimension(embeddingModel.dimension())
                .createIndex(true)
                .createTable(true)
                .dropTableFirst(true)
                .build();
    }

    @AfterEach
    void clearData() {
        if (embeddingStore != null) {
            embeddingStore.close();
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @Override
    @Disabled("Replaced by should_filter_by_metadata_override")
    protected void should_filter_by_metadata(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    @ParameterizedTest
    @MethodSource
    protected void should_filter_by_metadata_override(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    protected static Stream<Arguments> should_filter_by_metadata_override() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata()
                // Oracle seems to have trouble to extract a UUID as varbinary from a JSON payload
                // Disable until https://hibernate.atlassian.net/browse/HHH-20511 is fixed
                .filter(arguments -> !(arguments.get()[0] instanceof IsEqualTo isEqualTo)
                        || !(isEqualTo.comparisonValue() instanceof UUID));
    }

    @Override
    @Disabled("Replaced by should_filter_by_metadata_not_override")
    protected void should_filter_by_metadata_not(
            final Filter metadataFilter,
            final List<Metadata> matchingMetadatas,
            final List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata_not(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    @ParameterizedTest
    @MethodSource
    protected void should_filter_by_metadata_not_override(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata_not(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    protected static Stream<Arguments> should_filter_by_metadata_not_override() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata_not()
                // Oracle seems to have trouble to extract a UUID as varbinary from a JSON payload
                // Disable until https://hibernate.atlassian.net/browse/HHH-20511 is fixed
                .filter(arguments -> !(arguments.get()[0] instanceof IsNotEqualTo isNotEqualTo)
                        || !(isNotEqualTo.comparisonValue() instanceof UUID));
    }
}
