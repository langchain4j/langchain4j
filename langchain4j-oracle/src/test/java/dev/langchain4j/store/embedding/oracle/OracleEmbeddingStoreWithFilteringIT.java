package dev.langchain4j.store.embedding.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OracleEmbeddingStoreWithFilteringIT extends EmbeddingStoreWithFilteringIT {

    /**
     * A String of more than 32767 characters. Appending this to any other String will require a conversion to CLOB.
     * The value of 32767 is the maximum size of a VARCHAR for Oracle Database if the "MAX_STRING_SIZE" initialization
     * parameter is set to "EXTENDED". Otherwise, the maximum size is 4000. Either way, this length will force a CLOB
     * conversion.
     */
    private static final String STRING_32K =
            Stream.generate(() -> "A").limit(32768).collect(Collectors.joining());

    private final OracleEmbeddingStore embeddingStore = CommonTestOperations.newEmbeddingStore();

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return CommonTestOperations.getEmbeddingModel();
    }

    @Override
    protected void clearStore() {
        embeddingStore().removeAll();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        CommonTestOperations.dropTable();
    }

    /**
     * Verifies behavior when a TextSegment exceeds the maximum length of a VARCHAR.
     */
    @Test
    void clobTextSegment() {
        OracleEmbeddingStore oracleEmbeddingStore = CommonTestOperations.newEmbeddingStore();

        Embedding embedding0 = TestData.randomEmbedding();
        TextSegment textSegment0 = TextSegment.from("0 " + STRING_32K, Metadata.from("a", "A " + STRING_32K));
        String id0 = oracleEmbeddingStore.add(embedding0, textSegment0);

        float[] vector1 = embedding0.vector().clone();
        float[] vector2 = embedding0.vector().clone();
        vector1[0] += 10.0f;
        vector2[0] += 20.0f;
        Embedding embedding1 = new Embedding(vector1);
        Embedding embedding2 = new Embedding(vector2);
        TextSegment textSegment1 = TextSegment.from("1 " + STRING_32K, Metadata.from("b", "B " + STRING_32K));
        TextSegment textSegment2 = TextSegment.from("2 " + STRING_32K, Metadata.from("c", "C " + STRING_32K));
        List<String> ids = oracleEmbeddingStore.addAll(
                Arrays.asList(embedding1, embedding2), Arrays.asList(textSegment1, textSegment2));

        // Round 1: No filter. Just return CLOB valued text segments and metadata.
        List<EmbeddingMatch<TextSegment>> matches0 = oracleEmbeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding0)
                        .minScore(0d)
                        .maxResults(3)
                        .build())
                .matches();

        assertThat(matches0.size()).as(matches0.toString()).isEqualTo(3);
        assertThat(matches0.get(0).embeddingId()).isEqualTo(id0);
        assertThat(matches0.get(1).embeddingId()).isEqualTo(ids.get(0));
        assertThat(matches0.get(2).embeddingId()).isEqualTo(ids.get(1));
        assertThat(matches0.get(0).embedding()).isEqualTo(embedding0);
        assertThat(matches0.get(1).embedding()).isEqualTo(embedding1);
        assertThat(matches0.get(2).embedding()).isEqualTo(embedding2);
        assertThat(matches0.get(0).embedded()).isEqualTo(textSegment0);
        assertThat(matches0.get(1).embedded()).isEqualTo(textSegment1);
        assertThat(matches0.get(2).embedded()).isEqualTo(textSegment2);

        // Round 2: IsEqualTo on a substring of a metadata value. The substring length is 4000. This has SQLFilter
        // use a VARCHAR comparison. The JSON_VALUE function will have to handle a metadata value which is too
        // large for a VARCHAR. If it makes defective use of TRUNCATE, this test will fail.
        List<EmbeddingMatch<TextSegment>> matches1 = oracleEmbeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding0)
                        .minScore(0d)
                        .maxResults(3)
                        .filter(MetadataFilterBuilder.metadataKey("a")
                                .isEqualTo(
                                        textSegment0.metadata().getString("a").substring(0, 4000)))
                        .build())
                .matches();
        assertThat(matches1.isEmpty()).as(matches1.toString()).isTrue();

        // Round 3: IsGreaterThan on a substring of a metadata value. "CC".compareTo("C") returns a positive value, so
        // the filter should match. "BB".compareTo("C") returns a negative value, so the filter should not match the
        // other two text segments. If the JSON_VALUE function uses TRUNCATE, it will be equivalent to
        // "C".compareTo("C"), which returns 0, and the grater than comparison will evaluate to FALSE, which is wrong.
        List<EmbeddingMatch<TextSegment>> matches2 = oracleEmbeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding0)
                        .minScore(0d)
                        .maxResults(3)
                        .filter(MetadataFilterBuilder.metadataKey("a")
                                .isGreaterThan(
                                        textSegment0.metadata().getString("a").substring(0, 4000)))
                        .build())
                .matches();

        assertThat(matches2.size()).as(matches2.toString()).isEqualTo(1);
        assertThat(matches0.get(0).embeddingId()).isEqualTo(id0);
        assertThat(matches0.get(0).embedding()).isEqualTo(embedding0);
        assertThat(matches0.get(0).embedded()).isEqualTo(textSegment0);
    }

    @ParameterizedTest
    @MethodSource("should_filter_by_metadata")
    protected void should_filter_by_metadata(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        super.should_filter_by_metadata(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the super class to return all same test arguments, plus an additional set of arguments with long String
     * values. A "long" String is one with more than 32767 characters. That's a special case for the
     * {@link OracleEmbeddingStore}. Shorter Strings can be converted into VARCHAR values, but longer Strings must be
     * converted into CLOB values. The long Strings will exercise code found in the {@link SQLFilters} class, which
     * converts String values to CLOB or VARCHAR, depending on the length of the String.
     * </p>
     */
    @SuppressWarnings("unchecked")
    protected static Stream<Arguments> should_filter_by_metadata() {
        return Stream.concat(
                EmbeddingStoreWithFilteringIT.should_filter_by_metadata(),
                EmbeddingStoreWithFilteringIT.should_filter_by_metadata()
                        .map(Arguments::get)
                        .map(argumentsArray -> Arguments.of(
                                toClobFilter((Filter) argumentsArray[0]),
                                toClobMetadata((List<Metadata>) argumentsArray[1]),
                                toClobMetadata((List<Metadata>) argumentsArray[2]))));
    }

    /**
     * Converts a Filter into one which uses a long String value.
     *
     * @param filter Filter to convert. Not null.
     * @return The converted filter or the original filter if it doesn't use a short String value. Not null.
     */
    private static Filter toClobFilter(Filter filter) {
        if (filter instanceof IsEqualTo) {
            IsEqualTo isEqualTo = ((IsEqualTo) filter);
            return new IsEqualTo(isEqualTo.key(), toClobValue(isEqualTo.comparisonValue()));
        } else if (filter instanceof IsNotEqualTo) {
            IsNotEqualTo isNotEqualTo = (IsNotEqualTo) filter;
            return new IsNotEqualTo(isNotEqualTo.key(), toClobValue(isNotEqualTo.comparisonValue()));
        } else if (filter instanceof IsGreaterThan) {
            IsGreaterThan isGreaterThan = (IsGreaterThan) filter;
            return new IsGreaterThan(isGreaterThan.key(), toClobValue(isGreaterThan.comparisonValue()));
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            IsGreaterThanOrEqualTo isGreaterThanOrEqualTo = (IsGreaterThanOrEqualTo) filter;
            return new IsGreaterThanOrEqualTo(
                    isGreaterThanOrEqualTo.key(), toClobValue(isGreaterThanOrEqualTo.comparisonValue()));
        } else if (filter instanceof IsLessThan) {
            IsLessThan isLessThan = (IsLessThan) filter;
            return new IsLessThan(isLessThan.key(), toClobValue(isLessThan.comparisonValue()));
        } else if (filter instanceof IsLessThanOrEqualTo) {
            IsLessThanOrEqualTo isLessThanOrEqualTo = (IsLessThanOrEqualTo) filter;
            return new IsLessThanOrEqualTo(
                    isLessThanOrEqualTo.key(), toClobValue(isLessThanOrEqualTo.comparisonValue()));
        } else if (filter instanceof IsIn) {
            IsIn isIn = (IsIn) filter;
            return new IsIn(isIn.key(), toClobValue(isIn.comparisonValues()));
        } else if (filter instanceof IsNotIn) {
            IsNotIn isNotIn = (IsNotIn) filter;
            return new IsNotIn(isNotIn.key(), toClobValue(isNotIn.comparisonValues()));
        } else if (filter instanceof And) {
            And and = (And) filter;
            return new And(toClobFilter(and.left()), toClobFilter(and.right()));
        } else if (filter instanceof Or) {
            Or or = (Or) filter;
            return new Or(toClobFilter(or.left()), toClobFilter(or.right()));
        } else if (filter instanceof Not) {
            Not not = (Not) filter;
            return new Not(toClobFilter(not.expression()));
        } else {
            throw new UnsupportedOperationException("Need to add a case for: " + filter.getClass());
        }
    }

    /**
     * Converts a list of metadata into one with long String values.
     *
     * @param metadatas Metadata to convert. Not null.
     * @return List of converted metadata and any original metadata that doesn't use a short String value. Not null.
     */
    private static List<Metadata> toClobMetadata(List<Metadata> metadatas) {
        return metadatas.stream()
                .map(OracleEmbeddingStoreWithFilteringIT::toClobMetadata)
                .collect(Collectors.toList());
    }

    /**
     * Converts metadata into one with a long String value.
     *
     * @param metadata Metadata to convert. Not null.
     * @return Converted metadata, or the original metadata if it doesn't use a short String value. Not null.
     */
    private static Metadata toClobMetadata(Metadata metadata) {
        Map<String, Object> values = metadata.toMap();
        values.replaceAll((key, value) -> toClobValue(value));
        return new Metadata(values);
    }

    /**
     * Converts a collection of values into one that contains long String values.
     *
     * @param values Values to convert. Not null.
     * @return Converted values, or original values that aren't a short String value. Not null.
     */
    private static <T> Collection<T> toClobValue(Collection<T> values) {
        return values.stream()
                .map(OracleEmbeddingStoreWithFilteringIT::toClobValue)
                .collect(Collectors.toList());
    }

    /**
     * Converts a short String value into a long String value.
     *
     * @param value Value to convert. Not null.
     * @return The converted value, or the original value if it isn't a short String. Not null.
     */
    @SuppressWarnings("unchecked")
    private static <T> T toClobValue(T value) {
        if (!(value instanceof String)) return value;

        String stringValue = ((String) value);

        if (stringValue.length() >= STRING_32K.length()) return (T) stringValue;

        return (T) (stringValue + STRING_32K);
    }
}
