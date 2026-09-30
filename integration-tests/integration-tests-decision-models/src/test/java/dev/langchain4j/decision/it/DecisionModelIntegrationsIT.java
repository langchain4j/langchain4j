package dev.langchain4j.decision.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.router.DecisionModelChatModelRouter;
import dev.langchain4j.model.chat.router.RoutingChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.scoring.DecisionScoringModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DecisionModelQueryRouter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class DecisionModelIntegrationsIT {

    DecisionModel decisionModel = DecisionModels.typeSafe();

    @Test
    void should_rank_relevant_documents_first() {

        List<Double> scores = new DecisionScoringModel(decisionModel)
                .scoreAll(
                        List.of(
                                TextSegment.from("Our office is open Monday to Friday from 9 to 5."),
                                TextSegment.from("Go to Settings > Security and click 'Reset password'."),
                                TextSegment.from("Invoices are sent on the first day of each month.")),
                        "How do I reset my password?")
                .content();

        assertThat(scores.get(1)).isGreaterThan(0.5).isGreaterThan(scores.get(0)).isGreaterThan(scores.get(2));
    }

    @Test
    void should_route_query_to_relevant_retrievers_only() {

        ContentRetriever hr = mock(ContentRetriever.class);
        ContentRetriever wiki = mock(ContentRetriever.class);
        Map<ContentRetriever, String> retrievers = new LinkedHashMap<>();
        retrievers.put(hr, "HR policies: vacation, sick leave, benefits, expenses");
        retrievers.put(wiki, "Engineering wiki: services, deployments, on-call rotations");
        DecisionModelQueryRouter router = new DecisionModelQueryRouter(decisionModel, retrievers);

        assertThat(router.route(Query.from("How many vacation days do I have left this year?")))
                .containsExactly(hr);
        assertThat(router.route(Query.from("Hi there!"))).isEmpty();
    }

    @Test
    void should_route_chat_requests_by_description() {

        ChatModel small = ChatModelMock.thatAlwaysResponds("small");
        ChatModel large = ChatModelMock.thatAlwaysResponds("large");
        ChatModel chatModel = RoutingChatModel.builder()
                .route("simple", "Greetings, small talk and short factual questions", small)
                .route("complex", "Writing or debugging code, multi-step reasoning, detailed analysis", large)
                .router(new DecisionModelChatModelRouter(decisionModel))
                .defaultRoute("complex")
                .build();

        assertThat(chatModel.chat("Hi, how are you?")).isEqualTo("small");
        assertThat(chatModel.chat("Write a Java function that parses ISO 8601 durations and explain edge cases"))
                .isEqualTo("large");
    }

    @Test
    void should_route_chat_requests_by_one_of_several_descriptions() {

        ChatModel chatModel = RoutingChatModel.builder()
                .route("simple", List.of("Greetings and small talk", "Store opening hours", "Order tracking"),
                        ChatModelMock.thatAlwaysResponds("small"))
                .route("complex", List.of("Software debugging", "Contract law", "Tax planning"),
                        ChatModelMock.thatAlwaysResponds("large"))
                .router(new DecisionModelChatModelRouter(decisionModel))
                .defaultRoute("complex")
                .build();

        assertThat(chatModel.chat("Where is my package? Order 5521")).isEqualTo("small");
        assertThat(chatModel.chat("My landlord wants to keep the whole deposit for a scratch")).isEqualTo("large");
    }
}
