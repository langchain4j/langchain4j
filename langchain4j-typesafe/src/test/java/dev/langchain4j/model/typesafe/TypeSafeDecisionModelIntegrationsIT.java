package dev.langchain4j.model.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.router.DecisionModelChatModelRouter;
import dev.langchain4j.model.chat.router.RoutingChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.scoring.DecisionModelScoringModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DecisionModelQueryRouter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class TypeSafeDecisionModelIntegrationsIT {

    DecisionModel decisionModel = TypeSafeDecisionModel.builder()
            .apiKey(System.getenv("TYPESAFE_API_KEY"))
            .modelName("jev-1.13.0")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void scoring_model_should_rank_relevant_documents_first() {

        List<Double> scores = new DecisionModelScoringModel(decisionModel)
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
    void query_router_should_route_to_relevant_retrievers_only() {

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
    void routing_chat_model_should_route_by_description() {

        ChatModel small = new FixedChatModel("small");
        ChatModel large = new FixedChatModel("large");
        ChatModel chatModel = RoutingChatModel.builder()
                .route("simple", small, "Greetings, small talk and short factual questions")
                .route("complex", large, "Writing or debugging code, multi-step reasoning, detailed analysis")
                .router(new DecisionModelChatModelRouter(decisionModel))
                .defaultRoute("complex")
                .build();

        assertThat(chatModel.chat("Hi, how are you?")).isEqualTo("small");
        assertThat(chatModel.chat("Write a Java function that parses ISO 8601 durations and explain edge cases"))
                .isEqualTo("large");
    }

    static class FixedChatModel implements ChatModel {

        private final String answer;

        FixedChatModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
        }
    }
}
