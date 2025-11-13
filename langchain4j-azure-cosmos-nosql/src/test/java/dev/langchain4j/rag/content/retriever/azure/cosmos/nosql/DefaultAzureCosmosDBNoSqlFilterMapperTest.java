package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DefaultAzureCosmosDBNoSqlFilterMapperTest {

    private final AzureCosmosDBNoSqlFilterMapper mapper = new DefaultAzureCosmosDBNoSqlFilterMapper();

    @Test
    void map_nullFilter() {
        String result = mapper.map(null);
        assertThat(result).isEmpty();
    }

    @Test
    void map_handlesIsEqualTo() {
        IsEqualTo filter = new IsEqualTo("category", "electronics");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c.category = \"electronics\"");
    }

    @Test
    void map_handlesIsNotEqualTo() {
        IsNotEqualTo filter = new IsNotEqualTo("status", "inactive");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT c.status = \"inactive\")");
    }

    @Test
    void map_handlesIsGreaterThan() {
        IsGreaterThan filter = new IsGreaterThan("price", 100);
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c.price > 100");
    }

    @Test
    void map_handlesIsGreaterThanOrEqualTo() {
        IsGreaterThanOrEqualTo filter = new IsGreaterThanOrEqualTo("rating", 4.5);
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c.rating >= 4.5");
    }

    @Test
    void map_handlesIsLessThan() {
        IsLessThan filter = new IsLessThan("age", 30);
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c.age < 30");
    }

    @Test
    void map_handlesIsLessThanOrEqualTo() {
        IsLessThanOrEqualTo filter = new IsLessThanOrEqualTo("weight", 75.5);
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c.weight <= 75.5");
    }

    @Test
    void map_handlesIsIn() {
        IsIn filter = new IsIn("color", Arrays.asList("red", "blue", "green"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("c.color IN (\"blue\", \"green\", \"red\")");
    }

    @Test
    void map_handlesIsNotIn() {
        IsNotIn filter = new IsNotIn("size", Arrays.asList("XS", "XXL"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT c.size IN (\"XS\", \"XXL\"))");
    }

    @Test
    void map_handlesContainsString() {
        ContainsString filter = new ContainsString("description", "premium");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("CONTAINS(c.description, \"premium\")");
    }

    @Test
    void map_handlesFullTextContains() {
        FullTextContains filter = new FullTextContains("text", "red bicycle");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("FullTextContains(c.text, \"red bicycle\")");
    }

    @Test
    void map_handlesFullTextContainsAll() {
        FullTextContainsAll filter = new FullTextContainsAll("content", "red", "bicycle");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("FullTextContainsAll(c.content, \"red\", \"bicycle\")");
    }

    @Test
    void map_handlesFullTextContainsAny() {
        FullTextContainsAny filter = new FullTextContainsAny("text", "bicycle", "skateboard");
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("FullTextContainsAny(c.text, \"bicycle\", \"skateboard\")");
    }

    @Test
    void map_handlesAndOperator() {
        And filter = new And(new IsEqualTo("category", "sports"), new IsGreaterThan("price", 50));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(c.category = \"sports\" AND c.price > 50)");
    }

    @Test
    void map_handlesOrOperator() {
        Or filter = new Or(new IsEqualTo("brand", "Nike"), new IsEqualTo("brand", "Adidas"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(c.brand = \"Nike\" OR c.brand = \"Adidas\")");
    }

    @Test
    void map_handlesNotOperator() {
        Not filter = new Not(new IsEqualTo("status", "deleted"));
        String result = mapper.map(filter);
        assertThat(result).isEqualTo("(NOT c.status = \"deleted\")");
    }

    @Test
    void map_handlesComplexFilter() {
        Filter complexFilter = new And(
                new FullTextContains("description", "premium quality"),
                new Or(
                        new IsEqualTo("category", "electronics"),
                        new And(new IsEqualTo("category", "clothing"), new IsGreaterThan("rating", 4.0))));
        String result = mapper.map(complexFilter);
        assertThat(result)
                .isEqualTo(
                        "(FullTextContains(c.description, \"premium quality\") AND (c.category = \"electronics\" OR (c.category = \"clothing\" AND c.rating > 4.0)))");
    }

    @Test
    void map_throwsExceptionForUnsupportedFilter() {
        Filter unsupportedFilter = new Filter() {
            @Override
            public boolean test(Object object) {
                return false;
            }
        };
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> mapper.map(unsupportedFilter))
                .withMessageContaining("Unsupported filter type:");
    }
}
