package dev.langchain4j.rag.query.router;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.FAIL;
import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class LanguageModelQueryRouterTest {

    @Mock
    ContentRetriever catArticlesRetriever;

    @Mock
    ContentRetriever dogArticlesRetriever;

    @Test
    void should_route_to_single_retriever() {

        // given
        Query query = Query.from("Do Labradors shed?");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("2");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactly(dogArticlesRetriever);

        assertThat(model.userMessageText()).isEqualTo(
                """
                Based on the user query, determine the most suitable data source(s) \
                to retrieve relevant information from the following options:
                1: articles about cats
                2: articles about dogs
                It is very important that your answer consists of either a single number \
                or multiple numbers separated by commas and nothing else!
                User query: Do Labradors shed?""");
    }

    @Test
    void routeAsync_should_route_over_chatAsync() throws Exception {
        Query query = Query.from("Do Labradors shed?");
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("2");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        assertThat(router.routeAsync(query).get(5, SECONDS)).containsExactly(dogArticlesRetriever);
    }

    @Test
    void routeAsync_should_apply_fallback_strategy_on_unparseable_response() throws Exception {
        Query query = Query.from("Do Labradors shed?");
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("not a number"); // parse fails

        QueryRouter router = new LanguageModelQueryRouter(
                model, retrieverToDescription, LanguageModelQueryRouter.DEFAULT_PROMPT_TEMPLATE, ROUTE_TO_ALL);

        assertThat(router.routeAsync(query).get(5, SECONDS))
                .containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);
    }

    @Test
    void routeAsync_should_propagate_cancellation_instead_of_applying_fallback() {
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        // A chat model whose async call completes with a CancellationException (e.g. the caller cancelled the request)
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<ChatResponse> chatAsync(ChatRequest request) {
                return CompletableFuture.failedFuture(new CancellationException("cancelled"));
            }
        };
        // ROUTE_TO_ALL would route to every retriever on fallback - a cancellation must NOT be turned into that
        QueryRouter router = new LanguageModelQueryRouter(
                model, retrieverToDescription, LanguageModelQueryRouter.DEFAULT_PROMPT_TEMPLATE, ROUTE_TO_ALL);

        assertThatThrownBy(() -> router.routeAsync(Query.from("q")).get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(CancellationException.class);
    }

    @Test
    void routeAsync_should_propagate_an_error_instead_of_applying_fallback() {
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        // Like sync route()'s `catch (Exception)`, an Error is not a routing failure and must not become a fallback
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<ChatResponse> chatAsync(ChatRequest request) {
                return CompletableFuture.failedFuture(new Error("boom"));
            }
        };
        QueryRouter router = new LanguageModelQueryRouter(
                model, retrieverToDescription, LanguageModelQueryRouter.DEFAULT_PROMPT_TEMPLATE, ROUTE_TO_ALL);

        assertThatThrownBy(() -> router.routeAsync(Query.from("q")).get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(Error.class);
    }

    @Test
    void should_route_to_single_retriever_builder() {

        // given
        Query query = Query.from("Do Labradors shed?");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("2");

        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .build();

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactly(dogArticlesRetriever);

        assertThat(model.userMessageText()).isEqualTo(
                """
                Based on the user query, determine the most suitable data source(s) \
                to retrieve relevant information from the following options:
                1: articles about cats
                2: articles about dogs
                It is very important that your answer consists of either a single number \
                or multiple numbers separated by commas and nothing else!
                User query: Do Labradors shed?""");
    }

    @Test
    void should_route_to_multiple_retrievers() {

        // given
        Query query = Query.from("Which animal is the fluffiest?");

        Map<ContentRetriever, String> retrieverToDescription = new HashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("1, 2");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);
    }

    @Test
    void should_route_to_multiple_retrievers_with_custom_prompt_template() {

        // given
        PromptTemplate promptTemplate = PromptTemplate.from(
                "Which source should I use to get answer for '{{query}}'? " +
                        "Options: {{options}}'"
        );

        Query query = Query.from("Which animal is the fluffiest?");

        // LinkedHashMap is used to ensure a predictable order in the test
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("1, 2");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription, promptTemplate, FAIL);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);

        assertThat(model.userMessageText()).isEqualTo("""
                Which source should I use to get answer for \
                'Which animal is the fluffiest?'? \
                Options: \
                1: articles about cats
                2: articles about dogs'""");
    }

    @Test
    void should_not_route_by_default_when_LLM_returns_invalid_response() {

        // given
        Query query = Query.from("Hey what's up?");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Sorry, I don't know");

        final var retrieverToDescription = Map.of(
            catArticlesRetriever, "articles about cats",
            dogArticlesRetriever, "articles about dogs"
        );

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).isEmpty();
    }

    @Test
    void should_not_route_by_default_when_LLM_call_fails() {

        // given
        Query query = Query.from("Hey what's up?");

        ChatModelMock model = ChatModelMock.thatAlwaysThrowsException();

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).isEmpty();
    }

    @Test
    void should_route_to_all_retrievers_when_LLM_returns_invalid_response() {

        // given
        Query query = Query.from("Hey what's up?");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Sorry, I don't know");
        FallbackStrategy fallbackStrategy = ROUTE_TO_ALL;

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);
    }

    @Test
    void should_route_to_all_retrievers_when_LLM_call_fails() {

        // given
        Query query = Query.from("Hey what's up?");
        ChatModelMock model = ChatModelMock.thatAlwaysThrowsException();
        FallbackStrategy fallbackStrategy = ROUTE_TO_ALL;

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");


        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);
    }

    @Test
    void should_fail_when_LLM_returns_invalid_response() {

        // given
        Query query = Query.from("Hey what's up?");
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Sorry, I don't know");
        FallbackStrategy fallbackStrategy = FAIL;

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when-then
        assertThatThrownBy(() -> router.route(query))
                .hasRootCauseExactlyInstanceOf(NumberFormatException.class);
    }

    @Test
    void should_fail_when_LLM_call_fails() {

        // given
        Query query = Query.from("Hey what's up?");
        ChatModelMock model = ChatModelMock.thatAlwaysThrowsExceptionWithMessage("Something went wrong");
        FallbackStrategy fallbackStrategy = FAIL;

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = LanguageModelQueryRouter.builder()
                .chatModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when-then
        assertThatThrownBy(() -> router.route(query))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("Something went wrong");
    }
}
