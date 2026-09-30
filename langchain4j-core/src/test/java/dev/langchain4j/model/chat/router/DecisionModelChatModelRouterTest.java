package dev.langchain4j.model.chat.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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

        ChatModelRoutingResult route = router.route(request(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("Hi!"),
                AiMessage.from("Hello!"),
                UserMessage.from("Write a parser for this grammar")));

        assertThat(route).isEqualTo(ChatModelRoutingResult.route("complex"));
        assertThat(decisionModel.request().input())
                .isEqualTo(Map.of(
                        "messages",
                        List.of(
                                Map.of("role", "user", "text", "Hi!"),
                                Map.of("role", "assistant", "text", "Hello!"),
                                Map.of("role", "user", "text", "Write a parser for this grammar"))));
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

        assertThat(router.route(request(UserMessage.from("Hi!")))).isEqualTo(ChatModelRoutingResult.defaultRoute());
    }

    @Test
    void should_not_select_a_route_when_decision_model_fails() {

        ChatModelRouter router = new DecisionModelChatModelRouter(DecisionModelMock.thatAlwaysThrowsException());

        assertThat(router.route(request(UserMessage.from("Hi!")))).isEqualTo(ChatModelRoutingResult.defaultRoute());
        assertThat(router.routeAsync(request(UserMessage.from("Hi!"))).join()).isEqualTo(ChatModelRoutingResult.defaultRoute());
    }

    @Test
    void should_include_only_text_of_user_and_assistant_messages_in_previous_messages() {

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(choosing("complex", 0.9))
                .maxMessages(10)
                .build();

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("1")
                .name("weather")
                .arguments("{}")
                .build();
        router.route(request(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("What is the weather?"),
                AiMessage.from(toolCall),
                ToolExecutionResultMessage.from(toolCall, "sunny"),
                AiMessage.from("It is sunny."),
                UserMessage.from("And tomorrow?")));

        assertThat(decisionModel.request().input())
                .isEqualTo(Map.of(
                        "messages",
                        List.of(
                                Map.of("role", "user", "text", "What is the weather?"),
                                Map.of("role", "assistant", "text", "It is sunny."),
                                Map.of("role", "user", "text", "And tomorrow?"))));
    }

    @Test
    void should_not_select_a_route_without_user_message() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        assertThat(router.route(request(SystemMessage.from("You are a helpful assistant"))))
                .isEqualTo(ChatModelRoutingResult.defaultRoute());
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void should_choose_route_asynchronously() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        assertThat(router.routeAsync(request(UserMessage.from("Hi!"))).join()).isEqualTo(ChatModelRoutingResult.route("simple"));
    }

    @Test
    void should_select_the_only_route_without_asking_the_decision_model() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));
        ChatModelRoutingRequest request = ChatModelRoutingRequest.builder()
                .chatRequest(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .routes(List.of(ROUTES.get(1)))
                .build();

        assertThat(router.route(request)).isEqualTo(ChatModelRoutingResult.route("complex"));
        assertThat(router.routeAsync(request).join()).isEqualTo(ChatModelRoutingResult.route("complex"));
        assertThat(decisionModel.requests()).isEmpty();
    }

    @Test
    void should_describe_routes_without_description_by_their_name() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        router.route(ChatModelRoutingRequest.builder()
                .chatRequest(ChatRequest.builder().messages(UserMessage.from("Hi!")).build())
                .routes(List.of(new ChatModelRoute("simple", List.of()), ROUTES.get(1)))
                .build());

        assertThat(((ChoiceQuestion) decisionModel.request().questions().get("route")).options())
                .containsExactly(
                        Map.entry("simple", "simple"), Map.entry("complex", "Multi-step reasoning and code"));
    }

    @Test
    void should_ask_about_each_description_and_sum_the_probabilities_of_a_route() {

        List<ChatModelRoute> routes = List.of(
                new ChatModelRoute("simple", "Greetings and small talk"),
                new ChatModelRoute("complex", List.of("Writing or debugging code", "Legal contract analysis")));
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put("simple", 0.4);
        probabilities.put("complex#1", 0.3);
        probabilities.put("complex#2", 0.3);
        decisionModel = DecisionModelMock.thatAlwaysAnswers(Map.of(
                "route", ChoiceAnswer.builder().value("simple").probabilities(probabilities).build()));
        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(decisionModel)
                .minProbability(0.55)
                .build();

        ChatModelRoutingResult result = router.route(ChatModelRoutingRequest.builder()
                .chatRequest(ChatRequest.builder()
                        .messages(UserMessage.from("Review this NDA"))
                        .build())
                .routes(routes)
                .build());

        assertThat(result).isEqualTo(ChatModelRoutingResult.route("complex"));
        assertThat(((ChoiceQuestion) decisionModel.request().questions().get("route")).options())
                .containsExactly(
                        Map.entry("simple", "Greetings and small talk"),
                        Map.entry("complex#1", "Writing or debugging code"),
                        Map.entry("complex#2", "Legal contract analysis"));
    }

    @Test
    void should_reject_routes_whose_options_collide() {

        ChatModelRouter router = new DecisionModelChatModelRouter(choosing("simple", 0.9));

        assertThatThrownBy(() -> router.validate(List.of(
                        new ChatModelRoute("complex", List.of("Code", "Law")),
                        new ChatModelRoute("complex#1", "Something else"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complex#1");
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
    void should_fail_when_min_probability_is_set_but_no_probabilities_are_reported() {

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(DecisionModelMock.thatAlwaysAnswers(Map.of(
                        "route", ChoiceAnswer.builder().value("simple").build())))
                .minProbability(0.5)
                .build();

        assertThatThrownBy(() -> router.route(request(UserMessage.from("Hi!"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reported no probabilities");
        assertThat(router.routeAsync(request(UserMessage.from("Hi!"))))
                .failsWithin(Duration.ofSeconds(1))
                .withThrowableThat()
                .havingRootCause()
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_select_a_route_at_exactly_min_probability() {

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(choosing("simple", 0.7))
                .minProbability(0.7)
                .build();

        assertThat(router.route(request(UserMessage.from("Hi!")))).isEqualTo(ChatModelRoutingResult.route("simple"));
    }

    @Test
    void should_propagate_missing_async_support() {

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

        ChatModelRouter router = DecisionModelChatModelRouter.builder()
                .decisionModel(choosing("simple", 0.9))
                .maxMessages(1)
                .build();

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
