package dev.langchain4j.rag.query.router;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LanguageModelQueryRouterIT {

    @Mock
    ContentRetriever catArticlesRetriever;

    @Mock
    ContentRetriever dogArticlesRetriever;

    @ParameterizedTest
    @MethodSource("models")
    void should_route_to_single_retriever(ChatLanguageModel model) {

        // given
        Query query = Query.from("Do Labradors shed?");

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactly(dogArticlesRetriever);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_route_to_multiple_retrievers(ChatLanguageModel model) {

        // given
        Query query = Query.from("Tell me about animals");

        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        retrieverToDescription.put(catArticlesRetriever, "articles about cats");
        retrieverToDescription.put(dogArticlesRetriever, "articles about dogs");

        QueryRouter router = new LanguageModelQueryRouter(model, retrieverToDescription);

        // when
        Collection<ContentRetriever> retrievers = router.route(query);

        // then
        assertThat(retrievers).containsExactlyInAnyOrder(catArticlesRetriever, dogArticlesRetriever);
    }

    static Stream<Arguments> models() {
        return Stream.of(
                Arguments.of(
                        OpenAiChatModel.builder()
                                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                                .apiKey(System.getenv("OPENAI_API_KEY"))
                                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                                .logRequests(true)
                                .logResponses(true)
                                .build()
                )
                // TODO add more models
        );
    }
}
