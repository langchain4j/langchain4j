package dev.langchain4j.store.embedding.chroma;

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
import dev.langchain4j.store.embedding.filter.logical.Not;
import org.junit.jupiter.params.provider.Arguments;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ChromaEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    private static final ChromaDBContainer chroma = new ChromaDBContainer("chromadb/chroma:0.5.4");

    EmbeddingStore<TextSegment> embeddingStore = ChromaEmbeddingStore.builder()
            .baseUrl(chroma.getEndpoint())
            .collectionName(randomUUID())
            .logRequests(true)
            .logResponses(true)
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

    // in chroma compare filter only works with numbers
    protected static Stream<Arguments> should_filter_by_metadata() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata()
                .filter(arguments -> {
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
                        }
                );
    }

    // Chroma filters by *not* as following:
    // If you filter by "key" not equals "a", then in fact all items with "key" != "a" value are returned, but no items
    // without "key" metadata!
    // Therefore, all default *not* tests coming from parent class have to be rewritten here.
    protected static Stream<Arguments> should_filter_by_metadata_not() {
        return EmbeddingStoreWithFilteringIT.should_filter_by_metadata_not()
                .map(args -> {
                    Object[] arguments = args.get();
                    Filter filter = (Filter) arguments[0];

                    String key = getMetadataKey(filter);

                    List<Metadata> matchingMetadatas = (List<Metadata>) arguments[1];
                    List<Metadata> newMatchingMetadatas = matchingMetadatas.stream()
                            .filter(metadata -> metadata.containsKey(key))
                            .collect(toList());

                    List<Metadata> notMatchingMetadatas = (List<Metadata>) arguments[2];
                    List<Metadata> newNotMatchingMetadatas = new ArrayList<>(notMatchingMetadatas);
                    newNotMatchingMetadatas.addAll(matchingMetadatas.stream()
                            .filter(metadata -> !metadata.containsKey(key))
                            .collect(toList()));

                    assertThat(Stream.concat(newMatchingMetadatas.stream(), newNotMatchingMetadatas.stream()))
                            .containsExactlyInAnyOrderElementsOf(Stream.concat(matchingMetadatas.stream(), notMatchingMetadatas.stream()).collect(toList()));

                    return Arguments.of(filter, newMatchingMetadatas, newNotMatchingMetadatas);
                });
    }

    private static String getMetadataKey(Filter filter) {
        try {
            if (filter instanceof Not) {
                filter = ((Not) filter).expression();
            }
            Method method = filter.getClass().getMethod("key");
            return (String) method.invoke(filter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
