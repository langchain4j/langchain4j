package dev.langchain4j.test.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import dev.langchain4j.model.decision.request.FieldsInput;
import dev.langchain4j.model.decision.request.MessagesInput;
import dev.langchain4j.rag.content.Content;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class JudgedTextAssertTest {

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

    @Test
    void should_reject_null_judge() {
        assertThatThrownBy(() -> Judge.from((ChatModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatModel");
        assertThatThrownBy(() -> Judge.from((DecisionModel) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decisionModel");
    }

    @Test
    void judges_should_mark_tool_results_that_are_errors() {
        List<ChatMessage> conversation = List.of(
                UserMessage.from("Cancel my booking MS-777"),
                ToolExecutionResultMessage.builder()
                        .id("1")
                        .toolName("cancelBooking")
                        .text("Booking MS-777 not found")
                        .isError(true)
                        .build());
        ChatModelMock chatJudge = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "Yes.", "result": "SATISFIED"}]}""");
        DecisionModelMock decisionJudge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(chatJudge).assertThat(TEXT).given(conversation).satisfies("says it was not found");
        Judge.from(decisionJudge).assertThat(TEXT).given(conversation).satisfies("says it was not found");

        assertThat(((UserMessage) chatJudge.request().messages().get(1)).singleText())
                .contains("[error result of tool cancelBooking (id 1)] Booking MS-777 not found");
        // the decision model receives the tool result message itself, with its error flag
        assertThat(((MessagesInput) decisionJudge.request().input()).messages().get(1)).isEqualTo(conversation.get(1));
    }

    @Test
    void given_should_replace_user_message_and_conversation_given_before() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        Judge.from(judge)
                .assertThat(TEXT)
                .given("Cancel my booking MS-777")
                .given(CONVERSATION)
                .satisfies("answers the user's request");
        assertThat(judge.requests().get(0).input()).isInstanceOf(MessagesInput.class);

        Judge.from(judge)
                .assertThat(TEXT)
                .given(CONVERSATION)
                .given("Cancel my booking MS-777")
                .satisfies("answers the user's request");
        assertThat(((FieldsInput) judge.requests().get(1).input()).fields()).containsOnlyKeys("userMessage", "response");
    }

    @Test
    void should_show_conversation_in_failure() {
        ChatModelMock judge = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "No.", "result": "NOT_SATISFIED"}]}""");

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .given(CONVERSATION)
                        .satisfies("is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Conversation:")
                .hasMessageContaining("tool: [result of tool cancelBooking (id 1)] Booking MS-777 cancelled");
    }

    @Test
    void should_cut_long_conversation_messages_and_sources_in_failure() {
        ChatModelMock judge = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "No.", "result": "NOT_SATISFIED"}]}""");
        String longSystemMessage = "a".repeat(1000) + "b".repeat(250);
        String longSource = "c".repeat(300) + "d".repeat(50);

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .given(List.of(SystemMessage.from(longSystemMessage), UserMessage.from("Cancel MS-777")))
                        .satisfies("is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("system: " + "a".repeat(1000) + "...(250 more characters)")
                .hasMessageNotContaining("b".repeat(10));

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .basedOn(longSource)
                        .satisfies("is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("1. " + "c".repeat(300) + "...(50 more characters)")
                .hasMessageNotContaining("d".repeat(50));
    }

    @Test
    void should_cut_a_long_user_message_but_keep_the_judged_text_in_failure() {
        ChatModelMock judge = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "No.", "result": "NOT_SATISFIED"}]}""");
        String longUserMessage = "u".repeat(1000) + "v".repeat(40);
        String longText = "t".repeat(3000);

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(longText)
                        .given(longUserMessage)
                        .satisfies("is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("u".repeat(1000) + "...(40 more characters)")
                .hasMessageNotContaining("v".repeat(10))
                .hasMessageContaining(longText);
    }

    @Test
    void should_assert_with_any_judge_implementation() {
        Judge judge = request -> JudgeResponse.from(request.criteria().stream()
                .map(criterion -> JudgeVerdict.from(criterion, criterion.text().startsWith("is"), "custom judge"))
                .toList());

        judge.assertThat(TEXT).satisfies("is polite");
        assertThatThrownBy(() -> judge.assertThat(TEXT).satisfies("mentions a date"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Criterion: mentions a date")
                .hasMessageContaining("Verdict: custom judge");
    }

    @Test
    void should_reject_empty_conversation() {
        ChatModelMock judge = ChatModelMock.thatAlwaysResponds("{}");

        assertThatThrownBy(() -> Judge.from(judge).assertThat(TEXT).given(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversation");
    }

    @Test
    void should_reject_context_given_after_satisfies() {
        ChatModelMock judge = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "Yes.", "result": "SATISFIED"}]}""");
        JudgedTextAssert assertion = Judge.from(judge).assertThat(TEXT).satisfies("is polite");

        assertThatThrownBy(() -> assertion.given("Cancel my booking"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("given(...) must be called before satisfies(...)");
        assertThatThrownBy(() -> assertion.given(List.of(UserMessage.from("Cancel my booking"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("given(...) must be called before satisfies(...)");
        assertThatThrownBy(() -> assertion.basedOn("source"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("basedOn(...) must be called before satisfies(...)");
    }

    @Test
    void basedOn_should_accept_a_list_of_strings() {
        ChatModelMock judge = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "Yes.", "result": "SATISFIED"}]}""");

        Judge.from(judge)
                .assertThat(TEXT)
                .basedOn(List.of("Bookings: MS-123"))
                .satisfies("is polite");

        assertThat(((UserMessage) judge.request().messages().get(1)).singleText())
                .contains("<source number=\"1\">\nBookings: MS-123\n</source>");
    }

    @Test
    void should_support_descriptions_and_soft_assertions() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.1);

        assertThatThrownBy(() -> Judge.from(judge)
                        .assertThat(TEXT)
                        .as("refund scenario")
                        .satisfies("is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("[refund scenario]");

        SoftAssertions softly = new SoftAssertions();
        softly.check(() -> Judge.from(judge).assertThat(TEXT).satisfies("is polite"));
        softly.check(() -> Judge.from(judge).assertThat("Hello").satisfies("is short"));
        assertThat(softly.errorsCollected()).hasSize(2);
    }

    @Test
    void should_fail_on_null_text() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        assertThatThrownBy(() -> Judge.from(judge).assertThat(null).satisfies("is polite"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expecting actual not to be null");
        assertThat(judge.requests()).isEmpty();
    }

    @Test
    void should_reject_missing_criteria() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        assertThatThrownBy(() -> Judge.from(judge).assertThat(TEXT).satisfies(new String[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criteria");
        assertThatThrownBy(() -> Judge.from(judge).assertThat(TEXT).satisfies(new JudgeCriterion[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criteria");
        assertThatThrownBy(() -> Judge.from(judge).assertThat(TEXT).satisfies(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criterion");
        assertThat(judge.requests()).isEmpty();
    }

    @Test
    void should_fail_fast_when_a_custom_judge_returns_no_verdicts() {
        Judge judge = request -> JudgeResponse.from(List.of());

        assertThatThrownBy(() -> judge.assertThat(TEXT).satisfies("is polite"))
                .isExactlyInstanceOf(JudgeException.class)
                .hasMessageContaining("returned 0 verdicts for 1 criteria");
    }

    @Test
    void should_fail_fast_when_a_custom_judge_returns_fewer_verdicts_than_criteria() {
        Judge judge = request -> JudgeResponse.from(List.of(JudgeVerdict.from(request.criteria().get(0), true, "ok")));

        assertThatThrownBy(() -> judge.assertThat(TEXT).satisfies("is polite", "mentions the booking"))
                .isExactlyInstanceOf(JudgeException.class)
                .hasMessageContaining("returned 1 verdicts for 2 criteria");
    }

    @Test
    void should_fail_fast_when_a_custom_judge_returns_null() {
        Judge judge = request -> null;

        assertThatThrownBy(() -> judge.assertThat(TEXT).satisfies("is polite"))
                .isExactlyInstanceOf(JudgeException.class)
                .hasMessageContaining("returned no response");
    }

    @Test
    void should_fail_fast_when_a_custom_judge_returns_a_null_verdict() {
        Judge judge = request -> JudgeResponse.from(Arrays.asList((JudgeVerdict) null));

        assertThatThrownBy(() -> judge.assertThat(TEXT).satisfies("is polite"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verdict 1 cannot be null");
    }

    @Test
    void should_fail_fast_when_a_custom_judge_returns_a_verdict_for_another_criterion() {
        Judge judge = request -> JudgeResponse.from(List.of(JudgeVerdict.from(JudgeCriterion.from("is short"), true, "ok")));

        assertThatThrownBy(() -> judge.assertThat(TEXT).satisfies("is polite"))
                .isExactlyInstanceOf(JudgeException.class)
                .hasMessageContaining("verdict 1 is for \"is short\", but criterion 1 is \"is polite\"");
    }

    @Test
    void should_reject_based_on_contents_after_satisfies() {
        ChatModelMock judge = ChatModelMock.thatAlwaysResponds(
                """
                {"assessments": [{"conditionNumber": 1, "reasoning": "Yes.", "result": "SATISFIED"}]}""");
        JudgedTextAssert assertion = Judge.from(judge).assertThat(TEXT).satisfies("is polite");

        assertThatThrownBy(() -> assertion.basedOnContents(List.of(Content.from("source"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("basedOnContents(...) must be called before satisfies(...)");
    }

    @Test
    void should_reject_null_elements_in_context() {
        JudgedTextAssert assertion = Judge.from(ChatModelMock.thatAlwaysResponds("{}")).assertThat(TEXT);
        List<ChatMessage> conversationWithNull = new ArrayList<>();
        conversationWithNull.add(null);
        List<String> sourcesWithNull = new ArrayList<>();
        sourcesWithNull.add(null);
        List<Content> contentsWithNull = new ArrayList<>();
        contentsWithNull.add(null);

        assertThatThrownBy(() -> assertion.given(conversationWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
        assertThatThrownBy(() -> assertion.basedOn((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> assertion.basedOn(sourcesWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> assertion.basedOnContents(contentsWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> JudgeContext.from(conversationWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
        assertThatThrownBy(() -> JudgeRequest.builder().sources(sourcesWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    void should_not_wrap_exceptions_thrown_by_the_judge_model() {
        ChatModelMock judge = ChatModelMock.thatAlwaysThrowsExceptionWithMessage("model is down");

        assertThatThrownBy(() -> Judge.from(judge).assertThat(TEXT).satisfies("is polite"))
                .isNotInstanceOf(JudgeException.class)
                .hasMessage("model is down");
        assertThatThrownBy(() ->
                        Judge.from(judge).withOneRequestPerCriterion().assertThat(TEXT).satisfies("is polite", "is short"))
                .isNotInstanceOf(JudgeException.class)
                .hasMessage("model is down");
    }
}
