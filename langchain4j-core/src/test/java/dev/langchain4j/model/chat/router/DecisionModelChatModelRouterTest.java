package dev.langchain4j.model.chat.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelChatModelRouterTest {

    static final List<ChatModelRoute> ROUTES = List.of(
            new ChatModelRoute("simple", "Greetings and short factual questions"),
            new ChatModelRoute("complex", "Multi-step reasoning and code"));

    DecisionModelMock decisionModel;

    DecisionModelMock choosing(String option, double probability) {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put(option, probability);
        decisionModel = DecisionModelMock.thatAlwaysAnswers(Map.of(
                "route",
                ChoiceAnswer.builder().value(option).probabilities(probabilities).build()));
        return decisionModel;
    }

    static ChatModelRoutingRequest request(dev.langchain4j.data.message.ChatMessage... messages) {
        return new ChatModelRoutingRequest(ChatRequest.builder().messages(messages).build(), ROUTES);
    }

    @Test
    void should_choose_route_based_on_last_user_message() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("complex", 0.9));

        String route = router.route(request(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("Hi!"),
                AiMessage.from("Hello!"),
                UserMessage.from("Write a parser for this grammar")));

        assertThat(route).isEqualTo("complex");
        assertThat(decisionModel.request().input()).isEqualTo("Write a parser for this grammar");
        assertThat(decisionModel.request().questions())
                .containsEntry(
                        "route",
                        ChoiceQuestion.of(
                                "Which model should handle this request?",
                                Map.of(
                                        "simple", "Greetings and short factual questions",
                                        "complex", "Multi-step reasoning and code")));
    }

    @Test
    void should_not_select_a_route_below_min_probability() {

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(choosing("simple", 0.55))
                .minProbability(0.7)
                .build();

        assertThat(router.route(request(UserMessage.from("Hi!")))).isNull();
    }

    @Test
    void should_not_select_a_route_when_decision_model_fails() {

        ChatModelRouter router = new DecisionModelChatModelRouter(DecisionModelMock.thatAlwaysThrowsException());

        assertThat(router.route(request(UserMessage.from("Hi!")))).isNull();
        assertThat(router.routeAsync(request(UserMessage.from("Hi!"))).join()).isNull();
    }

    @Test
    void should_not_select_a_route_without_user_message() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        assertThat(router.route(request(SystemMessage.from("You are a helpful assistant"))))
                .isNull();
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void should_choose_route_asynchronously() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        assertThat(router.routeAsync(request(UserMessage.from("Hi!"))).join()).isEqualTo("simple");
    }

    @Test
    void should_select_the_only_route_without_asking_the_decision_model() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));
        ChatModelRoutingRequest request = new ChatModelRoutingRequest(
                ChatRequest.builder().messages(UserMessage.from("Hi!")).build(), List.of(ROUTES.get(1)));

        assertThat(router.route(request)).isEqualTo("complex");
        assertThat(router.routeAsync(request).join()).isEqualTo("complex");
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void should_require_route_descriptions() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        assertThatThrownBy(() -> router.route(new ChatModelRoutingRequest(
                        ChatRequest.builder().messages(UserMessage.from("Hi!")).build(),
                        List.of(new ChatModelRoute("simple", null), ROUTES.get(1)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route 'simple' has no description");
    }
}
