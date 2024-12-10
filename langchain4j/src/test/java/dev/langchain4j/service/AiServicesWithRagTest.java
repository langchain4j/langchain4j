package dev.langchain4j.service;

import static dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy.FAIL;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiServicesWithRagTest {

    @SuppressWarnings("UnusedReturnValue")
    private interface Assistant {
        String answer(String query);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a,b,c", ","})
    void should_fail_when_query_is_ambiguous(String modelResponse) {

        // given
        String query = "Hey what's up?";

        ChatLanguageModel model = ChatModelMock.thatAlwaysResponds(modelResponse);

        ContentRetriever contentRetriever = mock(ContentRetriever.class);

        Map<ContentRetriever, String> retrieverToDescription = new HashMap<>();
        retrieverToDescription.put(contentRetriever, "car rental company terms of use");

        QueryRouter queryRouter = LanguageModelQueryRouter.builder()
                .chatLanguageModel(model)
                .retrieverToDescription(retrieverToDescription)
                .fallbackStrategy(FAIL)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .queryRouter(queryRouter)
                        .build())
                .build();

        // when-then
        assertThatThrownBy(() -> assistant.answer(query)).hasMessageStartingWith("Failed to route query");

        verifyNoInteractions(contentRetriever);
    }
}
