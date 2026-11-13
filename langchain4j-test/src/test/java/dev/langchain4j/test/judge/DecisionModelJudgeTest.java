package dev.langchain4j.test.judge;

import static dev.langchain4j.data.message.ImageContent.DetailLevel.AUTO;
import static dev.langchain4j.data.message.ImageContent.DetailLevel.HIGH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.MessagesInput;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.RefusalAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import dev.langchain4j.rag.content.Content;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.langchain4j.model.output.TokenUsage;

class DecisionModelJudgeTest {

    private static final String TEXT = "I could not find booking MS-777. Could you check the booking number?";

    private static final List<ChatMessage> CONVERSATION = List.of(
            SystemMessage.from("You are a support agent."),
            UserMessage.from("Cancel my booking <MS-777>"),
            AiMessage.from(ToolExecutionRequest.builder()
                    .id("1")
                    .name("cancelBooking")
                    .arguments("{\"bookingNumber\":\"MS-777\"}")
                    .build()),
            ToolExecutionResultMessage.from("1", "cancelBooking", "Booking MS-777 cancelled"));

    private static JudgeRequest request(String text, String... criteria) {
        return JudgeRequest.builder()
                .text(text)
                .criteria(Arrays.stream(criteria).map(JudgeCriterion::from).toList())
                .build();
    }

    @Test
    void decision_model_judge_should_ask_one_yes_no_question_per_criterion_in_one_call() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judge)
                .assertThat(TEXT)
                .satisfies("says that the booking cannot be found", "asks to check the booking number");

        DecisionRequest request = judge.request();
        assertThat(((FieldsInput) request.input()).fields()).isEqualTo(Map.of("response", TEXT));
        assertThat(request.questions()).containsOnlyKeys("condition1", "condition2");
        YesNoQuestion first = (YesNoQuestion) request.questions().get("condition1");
        assertThat(first.text())
                .isEqualTo("Does the response satisfy this condition: says that the booking cannot be found");
        assertThat(first.yesWhen()).isNotBlank();
        assertThat(first.noWhen()).contains("only partly").contains("unclear");
        assertThat(((YesNoQuestion) request.questions().get("condition2")).text())
                .isEqualTo("Does the response satisfy this condition: asks to check the booking number");
    }

    @Test
    void decision_model_judge_should_ask_custom_question() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);
        DecisionModelJudge customJudge = Judge.from(judge)
                .withQuestion(criterion -> YesNoQuestion.builder()
                        .text("Would a support agent accept this response? It must: " + criterion.text())
                        .yesWhen("it clearly does")
                        .noWhen("it does not")
                        .build())
                .withThreshold(0.8);

        customJudge.assertThat(TEXT).satisfies("says that the booking cannot be found");

        YesNoQuestion question = (YesNoQuestion) judge.request().questions().get("condition1");
        assertThat(question.text())
                .isEqualTo("Would a support agent accept this response? It must: says that the booking cannot be found");
        assertThat(question.yesWhen()).isEqualTo("it clearly does");
        assertThat(question.noWhen()).isEqualTo("it does not");
    }

    @Test
    void decision_model_judge_should_keep_custom_question_when_threshold_changes() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.6);

        assertThatThrownBy(() -> Judge.from(judge)
                        .withQuestion(criterion -> YesNoQuestion.builder()
                                .text("Custom: " + criterion.text())
                                .build())
                        .withThreshold(0.7)
                        .assertThat(TEXT)
                        .satisfies("is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("P(yes) = 0.600, threshold = 0.700");
        assertThat(((YesNoQuestion) judge.request().questions().get("condition1")).text())
                .isEqualTo("Custom: is polite");
    }

    @Test
    void decision_model_judge_should_reject_null_question() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        assertThatThrownBy(() -> Judge.from(judge).withQuestion(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questions");
        assertThatThrownBy(() -> Judge.from(judge)
                        .withQuestion(criterion -> null)
                        .assertThat(TEXT)
                        .satisfies("is polite"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The question for criterion 1 is null");
    }

    @Test
    void decision_model_judge_should_fail_below_default_threshold() {
        DecisionModelMock judge = DecisionModelMock.thatAlwaysAnswers(
                Map.of("condition1", YesNoAnswer.from(0.5), "condition2", YesNoAnswer.from(0.49)));

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .satisfies("says that the booking cannot be found", "is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but 1 did not")
                .hasMessageContaining("Criterion: is polite")
                .hasMessageContaining("Verdict: P(yes) = 0.490, threshold = 0.500");
    }

    @Test
    void decision_model_judge_should_use_custom_threshold() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.6);

        Judge.from(judge).withThreshold(0.6).assertThat(TEXT).satisfies("is polite");

        assertThatThrownBy(() -> Judge.from(judge)
                        .withThreshold(0.61)
                        .assertThat(TEXT)
                        .satisfies("is polite"))
                .isInstanceOf(AssertionError.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.1, 1.1, Double.NaN})
    void decision_model_judge_should_reject_invalid_threshold(double threshold) {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        assertThatThrownBy(() -> Judge.from(judge).withThreshold(threshold))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void decision_model_judge_should_accept_threshold_of_one() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 1.0);

        Judge.from(judge).withThreshold(1.0).assertThat(TEXT).satisfies("is polite");
    }

    @Test
    void decision_model_judge_should_throw_on_refusal() {
        DecisionModelMock judge = DecisionModelMock.thatAlwaysAnswers(
                Map.of("condition1", RefusalAnswer.of(), "condition2", YesNoAnswer.from(0.9)));

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .satisfies("is polite", "mentions the start date"))
                .isExactlyInstanceOf(JudgeException.class)
                .hasMessage("The judge model refused to judge criterion 1");
    }

    @Test
    void decision_model_judge_should_send_user_message_as_context() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judge)
                .assertThat(TEXT)
                .given("Cancel my booking MS-777")
                .satisfies("answers the user's request");

        assertThat(((FieldsInput) judge.request().input()).fields())
                .isEqualTo(Map.of("userMessage", "Cancel my booking MS-777", "response", TEXT));
    }

    @Test
    void decision_model_judge_should_send_conversation_as_messages_with_text_as_last_assistant_message() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judge)
                .assertThat(TEXT)
                .given(CONVERSATION)
                .satisfies("answers the user's request");

        DecisionRequest request = judge.request();
        List<ChatMessage> expected = new ArrayList<>(CONVERSATION);
        expected.add(AiMessage.from(TEXT));
        assertThat(((MessagesInput) request.input()).messages()).isEqualTo(expected);
        YesNoQuestion question = (YesNoQuestion) request.questions().get("condition1");
        assertThat(question.text())
                .isEqualTo("Does the last assistant message of the conversation satisfy this condition: answers the"
                        + " user's request");
        assertThat(question.yesWhen()).contains("the last assistant message clearly and fully satisfies");
    }

    @Test
    void decision_model_judge_should_use_custom_question_with_conversation() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judge)
                .withQuestion(criterion -> YesNoQuestion.builder()
                        .text("Does the last assistant message do this: " + criterion.text())
                        .build())
                .assertThat(TEXT)
                .given(CONVERSATION)
                .satisfies("answers the user's request");

        assertThat(((YesNoQuestion) judge.request().questions().get("condition1")).text())
                .isEqualTo("Does the last assistant message do this: answers the user's request");
    }

    @Test
    void decision_model_judge_should_reject_conversation_with_separate_sources() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .given(CONVERSATION)
                        .basedOn("Bookings can be cancelled for free.")
                        .satisfies("answers the user's request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot judge a text given a conversation and separate sources");
        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .basedOnContents(List.of(Content.from("Bookings can be cancelled for free.")))
                        .given(CONVERSATION)
                        .satisfies("answers the user's request"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot judge a text given a conversation and separate sources");
        assertThatThrownBy(() -> Judge.from(judge)
                        .evaluate(JudgeRequest.builder()
                                .text(TEXT)
                                .context(JudgeContext.from(CONVERSATION))
                                .sources(List.of("Bookings can be cancelled for free."))
                                .criteria(JudgeCriterion.from("answers the user's request"))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot judge a text given a conversation and separate sources");
        assertThat(judge.requests()).isEmpty();
    }

    @Test
    void decision_model_judge_should_throw_on_content_filter() {
        DecisionModelMock judge = new DecisionModelMock(request -> {
            throw new ContentFilteredException("blocked by the content filter of the provider");
        });

        assertThatThrownBy(() -> Judge.from(judge).assertThat(TEXT).satisfies("is polite"))
                .isExactlyInstanceOf(JudgeException.class)
                .hasMessageContaining("The judge model refused to answer (content filter)")
                .hasCauseInstanceOf(ContentFilteredException.class);
    }

    @Test
    void decision_model_judge_should_throw_when_a_criterion_is_not_answered() {
        DecisionModelMock judge = DecisionModelMock.thatAlwaysAnswers(Map.of("condition1", YesNoAnswer.from(0.9)));

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .satisfies("says that the booking cannot be found", "is polite"))
                .isExactlyInstanceOf(JudgeException.class)
                .hasMessageContaining("The judge returned an invalid response")
                .hasMessageContaining("condition2");
    }

    @Test
    void decision_model_judge_should_return_verdicts_with_probabilities() {
        DecisionModelMock judge = DecisionModelMock.thatAlwaysAnswers(
                Map.of("condition1", YesNoAnswer.from(0.8), "condition2", YesNoAnswer.from(0.2)));

        List<JudgeVerdict> verdicts = Judge.from(judge).evaluate(request(TEXT, "is polite", "is short")).verdicts();

        assertThat(verdicts.get(0).isSatisfied()).isTrue();
        assertThat(verdicts.get(0).probability()).isEqualTo(0.8);
        assertThat(verdicts.get(0).explanation()).isEqualTo("P(yes) = 0.800, threshold = 0.500");
        assertThat(verdicts.get(1).isSatisfied()).isFalse();
        assertThat(verdicts.get(1).probability()).isEqualTo(0.2);
    }

    @Test
    void decision_model_judge_should_use_pass_and_fail_definitions() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judge)
                .assertThat(TEXT)
                .satisfies(
                        JudgeCriterion.from("asks to check the booking number")
                                .satisfiedWhen("asks the user to check or repeat the booking number"),
                        JudgeCriterion.from("is short").notSatisfiedWhen("it is longer than two sentences"));

        YesNoQuestion first = (YesNoQuestion) judge.request().questions().get("condition1");
        assertThat(first.yesWhen()).isEqualTo("asks the user to check or repeat the booking number");
        assertThat(first.noWhen()).contains("only partly");
        YesNoQuestion second = (YesNoQuestion) judge.request().questions().get("condition2");
        assertThat(second.yesWhen()).contains("clearly and fully");
        assertThat(second.noWhen()).isEqualTo("it is longer than two sentences");
    }

    @Test
    void decision_model_judge_should_send_sources() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judge)
                .assertThat(TEXT)
                .given("Cancel my booking MS-777")
                .basedOn("Bookings: MS-123")
                .satisfies("is supported by the sources");

        assertThat(((FieldsInput) judge.request().input()).fields())
                .isEqualTo(Map.of(
                        "userMessage", "Cancel my booking MS-777", "sources", List.of("Bookings: MS-123"), "response", TEXT));
    }

    @Test
    void decision_model_judge_should_send_images_without_a_chosen_detail_level_with_auto_detail() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);
        ImageContent notChosen = ImageContent.from("https://example.com/invoice.png");
        ImageContent chosen = ImageContent.from("https://example.com/receipt.png", ImageContent.DetailLevel.HIGH);

        Judge.from(judge)
                .assertThat(TEXT)
                .given(List.of(
                        UserMessage.builder()
                                .name("Ana")
                                .contents(List.of(TextContent.from("Check these"), notChosen, chosen))
                                .attributes(Map.of("channel", "web"))
                                .build(),
                        ToolExecutionResultMessage.builder()
                                .id("1")
                                .toolName("scan")
                                .contents(List.of(notChosen))
                                .isError(true)
                                .attributes(Map.of("source", "scanner"))
                                .build()))
                .satisfies("answers the user's request");

        List<ChatMessage> messages = ((MessagesInput) judge.request().input()).messages();
        UserMessage user = (UserMessage) messages.get(0);
        assertThat(((ImageContent) user.contents().get(1)).detailLevel()).isEqualTo(ImageContent.DetailLevel.AUTO);
        assertThat(user.contents().get(2)).isSameAs(chosen);
        assertThat(user.name()).isEqualTo("Ana");
        assertThat(user.attributes()).isEqualTo(Map.of("channel", "web"));
        ToolExecutionResultMessage toolResult = (ToolExecutionResultMessage) messages.get(1);
        assertThat(((ImageContent) toolResult.contents().get(0)).detailLevel())
                .isEqualTo(ImageContent.DetailLevel.AUTO);
        assertThat(toolResult.toolName()).isEqualTo("scan");
        assertThat(toolResult.id()).isEqualTo("1");
        assertThat(toolResult.isError()).isTrue();
        assertThat(toolResult.attributes()).isEqualTo(Map.of("source", "scanner"));
    }

    @Test
    void decision_model_judge_should_accept_a_conversation_with_empty_sources() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judge)
                .assertThat(TEXT)
                .given(List.of(UserMessage.from("Cancel my booking MS-777")))
                .basedOn(List.of())
                .satisfies("answers the user's request");

        assertThat(judge.request().input()).isInstanceOf(MessagesInput.class);
    }

    @Test
    void decision_model_judge_should_throw_judge_exception_when_the_response_has_no_answer_for_a_criterion() {
        DecisionModel decisionModel = mock(DecisionModel.class);
        when(decisionModel.decide(any(DecisionRequest.class)))
                .thenReturn(DecisionResponse.builder()
                        .answer("condition1", YesNoAnswer.from(0.9))
                        .build());

        assertThatThrownBy(() -> Judge.from(decisionModel).assertThat(TEXT).satisfies("is polite", "is short"))
                .isExactlyInstanceOf(JudgeException.class)
                .hasMessageContaining("The judge returned no answer for criterion 2");
    }

    @Test
    void decision_model_judge_should_report_the_token_usage_of_the_decision() {
        DecisionModelMock judge = DecisionModelMock.thatAlwaysAnswers(Map.of("condition1", YesNoAnswer.from(0.9)))
                .withTokenUsage(new TokenUsage(7, 3));

        JudgeResponse response = Judge.from(judge).evaluate(request(TEXT, "is polite"));

        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(7, 3));
    }

    @Test
    void decision_model_judge_should_report_no_token_usage_when_the_model_does_not() {
        DecisionModelMock judge = DecisionModelMock.thatAlwaysAnswers(Map.of("condition1", YesNoAnswer.from(0.9)));

        assertThat(Judge.from(judge).evaluate(request(TEXT, "is polite")).tokenUsage()).isNull();
    }


    @Test
    void decision_model_judge_should_reject_an_unknown_context_before_calling_the_model() {
        DecisionModelMock judge = DecisionModelMock.thatAlwaysAnswers(Map.of("condition1", YesNoAnswer.from(0.9)));
        JudgeContext unknown = new JudgeContext() {};

        assertThatThrownBy(() -> Judge.from(judge)
                        .evaluate(JudgeRequest.builder()
                                .text(TEXT)
                                .context(unknown)
                                .criteria(JudgeCriterion.from("is polite"))
                                .build()))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported JudgeContext");
        assertThat(judge.requests()).isEmpty();
    }

}
