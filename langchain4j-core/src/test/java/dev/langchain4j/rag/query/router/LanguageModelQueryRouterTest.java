package dev.langchain4j.rag.query.router;

import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

        ChatModelMock model = ChatModelMock.withStaticResponse("2");

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

        ChatModelMock model = ChatModelMock.withStaticResponse("2");

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

        ChatModelMock model = ChatModelMock.withStaticResponse("1, 2");

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

        ChatModelMock model = ChatModelMock.withStaticResponse("1, 2");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription, promptTemplate);

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
}