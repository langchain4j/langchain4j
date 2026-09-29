package dev.langchain4j.model.chat.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import java.time.Duration;
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

    static ChatModelRoutingRequest request(ChatMessage... messages) {
        return ChatModelRoutingRequest.builder()
                .chatRequest(ChatRequest.builder().messages(messages).build())
                .routes(ROUTES)
                .build();
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
        ChatModelRoutingRequest request = ChatModelRoutingRequest.builder()
                .chatRequest(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .routes(List.of(ROUTES.get(1)))
                .build();

        assertThat(router.route(request)).isEqualTo("complex");
        assertThat(router.routeAsync(request).join()).isEqualTo("complex");
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void should_require_route_descriptions() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        assertThatThrownBy(() -> router.validate(List.of(new ChatModelRoute("simple", null), ROUTES.get(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route 'simple' has no description");
        router.validate(ROUTES);
    }

    @Test
    void should_fail_when_decision_model_fails_with_fail_strategy() {

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(DecisionModelMock.thatAlwaysThrowsExceptionWithMessage("down"))
                .fallbackStrategy(DecisionModelChatModelRouter.FallbackStrategy.FAIL)
                .build();

        assertThatThrownBy(() -> router.route(request(UserMessage.from("Hi!")))).hasMessage("down");
        assertThat(router.routeAsync(request(UserMessage.from("Hi!"))))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .withMessage("down");
    }

    @Test
    void should_not_select_a_route_when_min_probability_is_set_but_no_probabilities_are_reported() {

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(DecisionModelMock.thatAlwaysAnswers(Map.of(
                        "route", ChoiceAnswer.builder().value("simple").build())))
                .minProbability(0.5)
                .build();

        assertThat(router.route(request(UserMessage.from("Hi!")))).isNull();
    }

    @Test
    void should_select_a_route_at_exactly_min_probability() {

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(choosing("simple", 0.7))
                .minProbability(0.7)
                .build();

        assertThat(router.route(request(UserMessage.from("Hi!")))).isEqualTo("simple");
    }

    @Test
    void should_propagate_missing_async_support_so_that_routing_can_be_offloaded() {

        ChatModelRouter router = new DecisionModelChatModelRouter(DecisionModelMock.thatAlwaysAnswers(Map.of(
                        "route", ChoiceAnswer.builder().value("simple").build()))
                .withoutAsyncSupport());

        assertThat(router.routeAsync(request(UserMessage.from("Hi!"))))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(AsyncNotSupportedException.class);
    }

    @Test
    void should_mark_attachments_of_multimodal_user_message() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        router.route(request(UserMessage.from(
                TextContent.from("What is in this picture?"), ImageContent.from("https://example.com/cat.png"))));
        assertThat(decisionModel.request().input()).isEqualTo("What is in this picture?\n[attached image]");

        router.route(request(UserMessage.from(ImageContent.from("https://example.com/cat.png"))));
        assertThat(decisionModel.requests().get(1).input()).isEqualTo("[attached image]");
    }

    @Test
    void should_include_previous_messages_up_to_max_messages() {

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(choosing("complex", 0.9))
                .maxMessages(3)
                .build();

        router.route(request(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("Hi!"),
                AiMessage.from("Hello!"),
                UserMessage.from("Can you write a parser for this grammar?"),
                AiMessage.from("Sure, which language?"),
                UserMessage.from("Java")));

        assertThat(decisionModel.request().input())
                .isEqualTo(Map.of(
                        "messages",
                        List.of(
                                Map.of("role", "user", "text", "Can you write a parser for this grammar?"),
                                Map.of("role", "assistant", "text", "Sure, which language?"),
                                Map.of("role", "user", "text", "Java"))));
    }

    @Test
    void should_use_custom_question_and_validate_configuration() {

        DecisionModelChatModelRouter.builder()
                .decisionModel(choosing("simple", 0.9))
                .question("Which assistant fits best?")
                .build()
                .route(request(UserMessage.from("Hi!")));

        assertThat(decisionModel.request().questions().get("route"))
                .isEqualTo(ChoiceQuestion.of(
                        "Which assistant fits best?",
                        Map.of(
                                "simple", "Greetings and short factual questions",
                                "complex", "Multi-step reasoning and code")));
        assertThatThrownBy(() -> DecisionModelChatModelRouter.builder()
                        .decisionModel(choosing("simple", 0.9))
                        .minProbability(-0.1)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minProbability");
        assertThatThrownBy(() -> DecisionModelChatModelRouter.builder()
                        .decisionModel(choosing("simple", 0.9))
                        .maxMessages(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessages");
        assertThatThrownBy(() -> DecisionModelChatModelRouter.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionModel");
    }
}
