package dev.langchain4j.store.embedding.chroma;

import static dev.langchain4j.internal.Utils.randomUUID;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChromaEmbeddingStoreV2IT extends EmbeddingStoreWithFilteringIT {

    @Container
    private static final ChromaDBContainer chroma =
            new ChromaDBContainer("chromadb/chroma:1.1.0").withExposedPorts(8000);

    EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
            .baseUrl("http://" + chroma.getHost() + ":" + chroma.getFirstMappedPort())
            .apiVersion(ChromaApiVersion.V2)
            .collectionName(randomUUID())
            .logRequests(false)
            .logResponses(false)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    @ParameterizedTest
    @MethodSource("should_filter_by_metadata_chroma")
    protected void should_filter_by_metadata(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    // in chroma compare filter only works with numbers
    protected static Stream<Arguments> should_filter_by_metadata_chroma() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata().filter(arguments -> {
            Filter filter = (Filter) arguments.get()[0];
            if (filter instanceof IsLessThan) {
                return ((IsLessThan) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsLessThanOrEqualTo) {
                return ((IsLessThanOrEqualTo) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsGreaterThan) {
                return ((IsGreaterThan) filter).comparisonValue() instanceof Number;
            } else if (filter instanceof IsGreaterThanOrEqualTo) {
                return ((IsGreaterThanOrEqualTo) filter).comparisonValue() instanceof Number;
            } else {
                return true;
            }
        });
    }
}
