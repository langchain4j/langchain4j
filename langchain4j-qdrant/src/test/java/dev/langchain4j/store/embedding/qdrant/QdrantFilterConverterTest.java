package dev.langchain4j.store.embedding.qdrant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import io.qdrant.client.grpc.Points;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class QdrantFilterConverterTest {

    @Test
    void containsFilter() {
        Filter filter = new ContainsString("string-value", "contains");
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter.getMust(0).getField().getKey()).isEqualTo("string-value");
        assertThat(convertedFilter.getMust(0).getField().getMatch().getText()).isEqualTo("contains");
    }

    @Test
    void isEqualToFilter() {
        Filter filter = new IsEqualTo("num-value", 5);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter.getMust(0).getField().getKey()).isEqualTo("num-value");
        assertThat(convertedFilter.getMust(0).getField().getMatch().getInteger())
                .isEqualTo(5);

        filter = new IsEqualTo("str-value", "value");
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter.getMust(0).getField().getKey()).isEqualTo("str-value");
        assertThat(convertedFilter.getMust(0).getField().getMatch().getKeyword())
                .isEqualTo("value");

        filter = new IsEqualTo("bool-value", true);
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter.getMust(0).getField().getKey()).isEqualTo("bool-value");
        assertThat(convertedFilter.getMust(0).getField().getMatch().getBoolean())
                .isEqualTo(true);
    }

    @Test
    void isNotEqualToFilter() {
        Filter filter = new IsNotEqualTo("num-value", 5);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter
                        .getMust(0)
                        .getFilter()
                        .getMustNot(0)
                        .getField()
                        .getKey())
                .isEqualTo("num-value");
        assertThat(convertedFilter
                        .getMust(0)
                        .getFilter()
                        .getMustNot(0)
                        .getField()
                        .getMatch()
                        .getInteger())
                .isEqualTo(5);

        filter = new IsNotEqualTo("str-value", "value");
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter
                        .getMust(0)
                        .getFilter()
                        .getMustNot(0)
                        .getField()
                        .getKey())
                .isEqualTo("str-value");
        assertThat(convertedFilter
                        .getMust(0)
                        .getFilter()
                        .getMustNot(0)
                        .getField()
                        .getMatch()
                        .getKeyword())
                .isEqualTo("value");

        filter = new IsNotEqualTo("bool-value", true);
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter
                        .getMust(0)
                        .getFilter()
                        .getMustNot(0)
                        .getField()
                        .getKey())
                .isEqualTo("bool-value");
        assertThat(convertedFilter
                        .getMust(0)
                        .getFilter()
                        .getMustNot(0)
                        .getField()
                        .getMatch()
                        .getBoolean())
                .isEqualTo(true);
    }

    @Test
    void isGreaterThanFilter() {
        Filter filter = new IsGreaterThan("key", 1);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);

        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter.getMust(0).getField().getRange().getGt()).isEqualTo(1);
    }

    @Test
    void isLessThanFilter() {
        Filter filter = new IsLessThan("key", 10);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);

        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter.getMust(0).getField().getRange().getLt()).isEqualTo(10);
    }

    @Test
    void isGreaterThanOrEqualToFilter() {
        Filter filter = new IsGreaterThanOrEqualTo("key", 1);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);

        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter.getMust(0).getField().getRange().getGte()).isEqualTo(1);
    }

    @Test
    void isLessThanOrEqualToFilter() {
        Filter filter = new IsLessThanOrEqualTo("key", 10);
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);

        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter.getMust(0).getField().getRange().getLte()).isEqualTo(10);
    }

    @Test
    void inFilter() {
        Filter filter = new IsIn("key", Arrays.asList(1, 2, 3));
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter
                        .getMust(0)
                        .getField()
                        .getMatch()
                        .getIntegers()
                        .getIntegersCount())
                .isEqualTo(3);

        filter = new IsIn("key", Arrays.asList("a", "b", "c"));
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter
                        .getMust(0)
                        .getField()
                        .getMatch()
                        .getKeywords()
                        .getStringsCount())
                .isEqualTo(3);
    }

    @Test
    void nInFilter() {
        Filter filter = new IsNotIn("key", Arrays.asList(1, 2, 3, 4));
        Points.Filter convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter
                        .getMust(0)
                        .getField()
                        .getMatch()
                        .getExceptIntegers()
                        .getIntegersCount())
                .isEqualTo(4);

        filter = new IsNotIn("key", Arrays.asList("a", "b", "c", "k"));
        convertedFilter = QdrantFilterConverter.convertExpression(filter);
        assertThat(convertedFilter).isNotNull();
        assertThat(convertedFilter.getMustCount()).isEqualTo(1);
        assertThat(convertedFilter
                        .getMust(0)
                        .getField()
                        .getMatch()
                        .getExceptKeywords()
                        .getStringsCount())
                .isEqualTo(4);
    }
}
