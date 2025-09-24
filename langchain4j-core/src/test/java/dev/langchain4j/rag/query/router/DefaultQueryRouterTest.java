package dev.langchain4j.rag.query.router;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import org.junit.jupiter.api.Test;

class DefaultQueryRouterTest {

    @Test
    void should_route_to_single_retriever() {

        // given
        ContentRetriever retriever1 = mock(ContentRetriever.class);
        QueryRouter router = new DefaultQueryRouter(retriever1);

        // when
        Collection<ContentRetriever> retrievers = router.route(Query.from("query"));

        // then
        assertThat(retrievers).containsExactly(retriever1);
    }

    @Test
    void should_route_to_multiple_retrievers() {

        // given
        ContentRetriever retriever1 = mock(ContentRetriever.class);
        ContentRetriever retriever2 = mock(ContentRetriever.class);
        QueryRouter router = new DefaultQueryRouter(asList(retriever1, retriever2));

        // when
        Collection<ContentRetriever> retrievers = router.route(Query.from("query"));

        // then
        assertThat(retrievers).containsExactly(retriever1, retriever2);
    }

    @Test
    void should_route_to_multiple_retrievers_varargs() {

        // given
        ContentRetriever retriever1 = mock(ContentRetriever.class);
        ContentRetriever retriever2 = mock(ContentRetriever.class);
        QueryRouter router = new DefaultQueryRouter(retriever1, retriever2);

        // when
        Collection<ContentRetriever> retrievers = router.route(Query.from("query"));

        // then
        assertThat(retrievers).containsExactly(retriever1, retriever2);
    }

    @Test
    void should_handle_empty_retrievers_list() {
        // when/then
        assertThatThrownBy(() -> new DefaultQueryRouter(asList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentRetrievers cannot be null or empty");
    }

    @Test
    void should_handle_null_retrievers_list() {
        // when/then
        assertThatThrownBy(() -> new DefaultQueryRouter((Collection<ContentRetriever>) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentRetrievers cannot be null or empty");
    }

    @Test
    void should_preserve_retriever_order() {
        // given
        ContentRetriever retriever1 = mock(ContentRetriever.class);
        ContentRetriever retriever2 = mock(ContentRetriever.class);
        ContentRetriever retriever3 = mock(ContentRetriever.class);
        QueryRouter router = new DefaultQueryRouter(retriever1, retriever2, retriever3);

        // when
        Collection<ContentRetriever> retrievers = router.route(Query.from("query"));

        // then
        assertThat(retrievers).containsExactly(retriever1, retriever2, retriever3);
    }
}
