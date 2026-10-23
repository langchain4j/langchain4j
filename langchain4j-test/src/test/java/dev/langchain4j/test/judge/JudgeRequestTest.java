package dev.langchain4j.test.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.decision.mock.DecisionModelMock;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeRequestTest {

    private static final String TEXT = "I could not find booking MS-777. Could you check the booking number?";

    private static JudgeRequest request(String text, String... criteria) {
        return JudgeRequest.builder()
                .text(text)
                .criteria(Arrays.stream(criteria).map(JudgeCriterion::from).toList())
                .build();
    }

    @Test
    void verdict_should_validate_its_arguments() {
        JudgeCriterion criterion = JudgeCriterion.from("is polite");

        assertThat(JudgeVerdict.from(criterion, true, "ok").probability()).isNull();
        assertThat(JudgeVerdict.from(criterion, true, "ok", 0.9).probability()).isEqualTo(0.9);
        assertThatThrownBy(() -> JudgeVerdict.from(criterion, true, "ok", 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probability");
        assertThatThrownBy(() -> JudgeVerdict.from(null, true, "ok"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criterion");
        assertThatThrownBy(() -> JudgeVerdict.from(criterion, true, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explanation");
    }

    @Test
    void evaluate_should_reject_invalid_arguments() {
        DecisionModelMock judge = DecisionModelMock.thatAnswersYesNoQuestions(question -> 0.9);

        assertThatThrownBy(() -> Judge.from(judge).evaluate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request");
        assertThatThrownBy(() -> request(null, "is polite"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> JudgeRequest.builder().text(TEXT).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criteria");
        assertThatThrownBy(() -> JudgeRequest.builder()
                        .text(TEXT)
                        .criteria(List.of())
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criteria");
        assertThatThrownBy(() -> JudgeRequest.builder()
                        .text(TEXT)
                        .userMessage(" ")
                        .criteria(JudgeCriterion.from("is polite"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userMessage");
        assertThatThrownBy(() -> JudgeRequest.builder().criteria(JudgeCriterion.from("is polite"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criterion");
        assertThatThrownBy(() -> JudgeCriterion.from(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criterion");
        assertThatThrownBy(() -> JudgeCriterion.from("is polite").withPassWhen(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passWhen");
        assertThat(judge.requests()).isEmpty();
    }

    @Test
    void request_should_reject_both_user_message_and_conversation() {
        assertThatThrownBy(() -> JudgeRequest.builder()
                        .text(TEXT)
                        .userMessage("Where is my booking?")
                        .conversation(List.of(UserMessage.from("Where is my booking?")))
                        .criteria(JudgeCriterion.from("is polite"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either the user message or the conversation");
    }

    @Test
    void request_should_reject_an_empty_conversation() {
        assertThatThrownBy(() -> JudgeRequest.builder()
                        .text(TEXT)
                        .conversation(List.of())
                        .criteria(JudgeCriterion.from("is polite"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversation");
    }
}
