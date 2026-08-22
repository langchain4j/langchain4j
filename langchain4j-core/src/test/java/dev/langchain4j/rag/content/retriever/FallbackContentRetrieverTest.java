package dev.langchain4j.rag.content.retriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FallbackContentRetrieverTest {

    private static final Query QUERY = Query.from("query");

    @Test
    void should_return_primary_results_without_calling_fallback() {
        List<Content> primaryResults = List.of(Content.from("primary"));
        AtomicInteger fallbackCalls = new AtomicInteger();
        ContentRetriever fallback = ignored -> {
            fallbackCalls.incrementAndGet();
            return List.of(Content.from("fallback"));
        };

        FallbackContentRetriever retriever = new FallbackContentRetriever(ignored -> primaryResults, fallback);

        assertThat(retriever.retrieve(QUERY)).isSameAs(primaryResults);
        assertThat(fallbackCalls).hasValue(0);
    }

    @Test
    void should_fallback_on_empty_or_null_primary_results() {
        List<Content> fallbackResults = List.of(Content.from("fallback"));
        ContentRetriever fallback = ignored -> fallbackResults;

        assertThat(new FallbackContentRetriever(ignored -> List.of(), fallback).retrieve(QUERY))
                .isSameAs(fallbackResults);
        assertThat(new FallbackContentRetriever(ignored -> null, fallback).retrieve(QUERY))
                .isSameAs(fallbackResults);
    }

    @Test
    void should_support_a_domain_specific_fallback_condition() {
        Content noRows = Content.from("Result: header only");
        List<Content> fallbackResults = List.of(Content.from("fallback"));
        FallbackContentRetriever retriever = FallbackContentRetriever.builder()
                .primaryRetriever(ignored -> List.of(noRows))
                .fallbackRetriever(ignored -> fallbackResults)
                .fallbackCondition(results -> results.size() == 1
                        && results.get(0).textSegment().text().endsWith("header only"))
                .build();

        assertThat(retriever.retrieve(QUERY)).isSameAs(fallbackResults);
    }

    @Test
    void should_fallback_on_primary_exception_by_default() {
        List<Content> fallbackResults = List.of(Content.from("fallback"));
        ContentRetriever primary = ignored -> {
            throw new IllegalStateException("primary failed");
        };

        assertThat(new FallbackContentRetriever(primary, ignored -> fallbackResults).retrieve(QUERY))
                .isSameAs(fallbackResults);
    }

    @Test
    void should_optionally_propagate_primary_exception() {
        IllegalStateException failure = new IllegalStateException("primary failed");
        ContentRetriever primary = ignored -> {
            throw failure;
        };
        FallbackContentRetriever retriever = FallbackContentRetriever.builder()
                .primaryRetriever(primary)
                .fallbackRetriever(ignored -> List.of(Content.from("fallback")))
                .fallbackOnException(false)
                .build();

        assertThatThrownBy(() -> retriever.retrieve(QUERY)).isSameAs(failure);
    }

    @Test
    void should_preserve_primary_exception_when_fallback_also_fails() {
        IllegalStateException primaryFailure = new IllegalStateException("primary failed");
        IllegalArgumentException fallbackFailure = new IllegalArgumentException("fallback failed");
        FallbackContentRetriever retriever = new FallbackContentRetriever(
                ignored -> {
                    throw primaryFailure;
                },
                ignored -> {
                    throw fallbackFailure;
                });

        assertThatThrownBy(() -> retriever.retrieve(QUERY))
                .isSameAs(fallbackFailure)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(primaryFailure));
    }

    @Test
    void should_validate_required_retrievers() {
        assertThatThrownBy(() -> FallbackContentRetriever.builder()
                        .fallbackRetriever(ignored -> List.of())
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("primaryRetriever cannot be null");
    }
}
