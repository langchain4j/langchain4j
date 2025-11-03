package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.WildcardQuery;
import dev.langchain4j.store.embedding.filter.comparison.Like;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchMetadataFilterMapperTest {

    @ParameterizedTest
    @CsvSource({
            "%mars%, LIKE, metadata.planet.keyword, *mars*",
            "mars%, LIKE, metadata.planet.keyword, mars*",
            "%mars, LIKE, metadata.planet.keyword, *mars",
            "_ars, LIKE, metadata.planet.keyword, ?ars",
            "%mars%, LIKE, metadata.planet.keyword, *mars*",
            "%mars%, ILIKE, metadata.planet.lowercase, *mars*",
            "%mars%, ILIKE, metadata.planet.lowercase, *mars*",
            "m_r%, LIKE, metadata.planet.keyword, m?r*"
    })
    void map_likeFilter_nonNegated(String pattern,
                                   String operator,
                                   String expectedField,
                                   String expectedValue) {

        Like like = new Like("planet", pattern, Like.Operator.valueOf(operator), false);
        Query query = ElasticsearchMetadataFilterMapper.map(like);

        assertThat(query.bool().filter()).isNotEmpty();
        WildcardQuery wildcard = query.bool().filter().get(0).wildcard();
        assertWildcard(wildcard, expectedField,expectedValue);
    }

    @ParameterizedTest
    @CsvSource({
            "%mars%, LIKE, metadata.planet.keyword, *mars*",
            "_ars%, ILIKE, metadata.planet.lowercase, ?ars*"
    })
    void map_likeFilter_negated(String pattern,
                                String operator,
                                String expectedField,
                                String expectedValue) {

        Like like = new Like("planet", pattern, Like.Operator.valueOf(operator), true);
        Query query = ElasticsearchMetadataFilterMapper.map(like);

        // validate usage of mustnot
        assertThat(query.bool().mustNot()).isNotEmpty();
        WildcardQuery wildcard = query.bool().mustNot().get(0).wildcard();
        assertWildcard(wildcard, expectedField,expectedValue);
    }

    private void assertWildcard(WildcardQuery wildcard,
                                String expectedField,
                                String expectedValue) {
        assertThat(wildcard).isNotNull();
        assertThat(wildcard.field()).isEqualTo(expectedField);
        assertThat(wildcard.value()).isEqualTo(expectedValue);

    }

}
