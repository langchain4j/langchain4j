package dev.langchain4j.rag.query.transformer;

import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultQueryTransformerTest {

    @Test
    void should_return_same_query() {

        // given
        QueryTransformer transformer = new DefaultQueryTransformer();
        Query query = Query.from("query");

        // when
        Collection<Query> transformed = transformer.transform(query);

        // then
        assertThat(transformed).containsExactly(query);
    }

    @Test
    void transformAsync_should_return_failed_future_when_transform_throws() {

        IllegalStateException failure = new IllegalStateException("cannot transform");
        QueryTransformer transformer = new DefaultQueryTransformer() {
            @Override
            public Collection<Query> transform(Query query) {
                throw failure;
            }
        };

        assertThat(transformer.transformAsync(Query.from("query")))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isSameAs(failure);
    }
}