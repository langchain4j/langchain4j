package dev.langchain4j.rag.content.retriever.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class ElasticsearchClientSideHybridContentRetrieverTest {

    private static final Query QUERY = Query.from("query");
    private static final Executor SAME_THREAD_EXECUTOR = Runnable::run;

    @Test
    void should_fuse_vector_and_full_text_results_and_remove_duplicates() {
        Content shared = Content.from("shared");
        Content vectorOnly = Content.from("vector only");
        Content fullTextOnly = Content.from("full text only");
        ContentRetriever vectorRetriever = ignored -> List.of(shared, vectorOnly);
        ContentRetriever fullTextRetriever = ignored -> List.of(fullTextOnly, shared);

        ElasticsearchClientSideHybridContentRetriever retriever = new ElasticsearchClientSideHybridContentRetriever(
                vectorRetriever, fullTextRetriever, 3, 60, SAME_THREAD_EXECUTOR);

        assertThat(retriever.retrieve(QUERY)).containsExactly(shared, fullTextOnly, vectorOnly);
    }

    @Test
    void should_limit_the_fused_results() {
        ContentRetriever vectorRetriever = ignored -> List.of(Content.from("one"), Content.from("two"));
        ContentRetriever fullTextRetriever = ignored -> List.of(Content.from("three"), Content.from("four"));

        ElasticsearchClientSideHybridContentRetriever retriever = new ElasticsearchClientSideHybridContentRetriever(
                vectorRetriever, fullTextRetriever, 2, 60, SAME_THREAD_EXECUTOR);

        assertThat(retriever.retrieve(QUERY)).hasSize(2);
    }

    @Test
    void should_propagate_the_original_retrieval_exception() {
        IllegalStateException failure = new IllegalStateException("Elasticsearch failed");
        ContentRetriever vectorRetriever = ignored -> {
            throw failure;
        };
        ContentRetriever fullTextRetriever = ignored -> List.of();
        ElasticsearchClientSideHybridContentRetriever retriever = new ElasticsearchClientSideHybridContentRetriever(
                vectorRetriever, fullTextRetriever, 3, 60, SAME_THREAD_EXECUTOR);

        assertThatThrownBy(() -> retriever.retrieve(QUERY)).isSameAs(failure);
    }

    @Test
    void should_validate_required_configuration() {
        assertThatThrownBy(() ->
                        ElasticsearchClientSideHybridContentRetriever.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("client cannot be null");
    }
}
