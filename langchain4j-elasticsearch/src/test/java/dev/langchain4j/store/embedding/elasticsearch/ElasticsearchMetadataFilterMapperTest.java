package dev.langchain4j.store.embedding.elasticsearch;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ElasticsearchMetadataFilterMapperTest {

    @Test
    void string_filters_use_the_keyword_subfield() {
        String longSource = "https://docs.example.com/" + "a".repeat(300);

        assertThat(field(metadataKey("ingested_on").isEqualTo("2026-11-21"))).isEqualTo("metadata.ingested_on.keyword");
        assertThat(field(metadataKey("ingested_on").isNotEqualTo("2026-11-21")))
                .isEqualTo("metadata.ingested_on.keyword");
        assertThat(field(metadataKey("source").isIn(longSource))).isEqualTo("metadata.source.keyword");
        assertThat(field(metadataKey("source").isNotIn(longSource))).isEqualTo("metadata.source.keyword");
        assertThat(field(metadataKey("title").isGreaterThan("m"))).isEqualTo("metadata.title.keyword");
        assertThat(field(metadataKey("title").isGreaterThanOrEqualTo("m"))).isEqualTo("metadata.title.keyword");
        assertThat(field(metadataKey("title").isLessThan("b"))).isEqualTo("metadata.title.keyword");
        assertThat(field(metadataKey("title").isLessThanOrEqualTo("b"))).isEqualTo("metadata.title.keyword");
        assertThat(field(metadataKey("id").isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"))))
                .isEqualTo("metadata.id.keyword");
    }

    @Test
    void numeric_filters_keep_the_typed_field() {
        assertThat(field(metadataKey("year").isEqualTo(2026))).isEqualTo("metadata.year");
        assertThat(field(metadataKey("year").isNotEqualTo(2026L))).isEqualTo("metadata.year");
        assertThat(field(metadataKey("year").isIn(2020, 2026))).isEqualTo("metadata.year");
        assertThat(field(metadataKey("year").isNotIn(2020L))).isEqualTo("metadata.year");
        assertThat(field(metadataKey("year").isGreaterThan(2020))).isEqualTo("metadata.year");
        assertThat(field(metadataKey("year").isGreaterThanOrEqualTo(2020L))).isEqualTo("metadata.year");
        assertThat(field(metadataKey("score").isLessThan(1.5d))).isEqualTo("metadata.score");
        assertThat(field(metadataKey("score").isLessThanOrEqualTo(1.5f))).isEqualTo("metadata.score");
    }

    private static String field(Filter filter) {
        BoolQuery bool = ElasticsearchMetadataFilterMapper.map(filter).bool();
        Query clause =
                bool.filter().isEmpty() ? bool.mustNot().get(0) : bool.filter().get(0);
        if (clause.isTerm()) {
            return clause.term().field();
        }
        if (clause.isTerms()) {
            return clause.terms().field();
        }
        if (clause.isRange()) {
            return clause.range().untyped().field();
        }
        throw new AssertionError(clause._kind());
    }
}
