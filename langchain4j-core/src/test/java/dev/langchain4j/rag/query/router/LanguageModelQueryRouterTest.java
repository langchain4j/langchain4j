package dev.langchain4j.rag.query.router;

import dev.langchain4j.model.chat.mock.ChatModelMock;
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

import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.FAIL;
import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.ROUTE_TO_ALL;
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
                "Based on the user query, determine the most suitable data source(s) " +
                        "to retrieve relevant information from the following options:\n" +
                        "1: articles about cats\n" +
                        "2: articles about dogs\n" +
                        "It is very important that your answer consists of either a single number " +
                        "or multiple numbers separated by commas and nothing else!\n" +
                        "User query: Do Labradors shed?");
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
                .chatLanguageModel(model)
                .retrieverToDescription(retrieverToDescription)
                .build();

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactly(dogArticlesRetriever);

        assertThat(model.userMessageText()).isEqualTo(
                "Based on the user query, determine the most suitable data source(s) " +
                        "to retrieve relevant information from the following options:\n" +
                        "1: articles about cats\n" +
                        "2: articles about dogs\n" +
                        "It is very important that your answer consists of either a single number " +
                        "or multiple numbers separated by commas and nothing else!\n" +
                        "User query: Do Labradors shed?");
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

        assertThat(model.userMessageText()).isEqualTo("Which source should I use to get answer for " +
                "'Which animal is the fluffiest?'? " +
                "Options: " +
                "1: articles about cats\n" +
                "2: articles about dogs'");
    }

    @Test
    void should_not_route_by_default_when_LLM_returns_invalid_response() {

        // given
        Query query = Query.from("Hey what's up?");

        ChatModelMock model = ChatModelMock.thatAlwaysResponds("Sorry, I don't know");

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
                .chatLanguageModel(model)
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
                .chatLanguageModel(model)
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
                .chatLanguageModel(model)
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
                .chatLanguageModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(fallbackStrategy)
                .build();

        // when-then
        assertThatThrownBy(() -> router.route(query))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("Something went wrong");
    }
}