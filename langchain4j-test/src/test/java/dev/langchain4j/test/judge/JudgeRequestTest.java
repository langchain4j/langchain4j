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
                        .context(JudgeContext.from(" "))
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
        assertThatThrownBy(() -> JudgeCriterion.from("is polite").satisfiedWhen(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("satisfiedWhen");
        assertThat(judge.requests()).isEmpty();
    }

    @Test
    void request_should_keep_the_context() {
        JudgeContext conversation = JudgeContext.from(List.of(UserMessage.from("Where is my booking?")));

        JudgeRequest request = JudgeRequest.builder()
                .text(TEXT)
                .context(conversation)
                .criteria(JudgeCriterion.from("is polite"))
                .build();

        assertThat(request.context()).isEqualTo(conversation);
        assertThat(request.toString()).contains("context=ConversationContext{messages=[");
    }

    @Test
    void request_should_have_no_context_by_default() {
        JudgeRequest request = JudgeRequest.builder()
                .text(TEXT)
                .criteria(JudgeCriterion.from("is polite"))
                .build();

        assertThat(request.context()).isNull();
    }

    @Test
    void request_should_compare_the_context() {
        JudgeRequest.Builder builder = JudgeRequest.builder().text(TEXT).criteria(JudgeCriterion.from("is polite"));

        JudgeRequest withQuestion = builder.context(JudgeContext.from("Where is my booking?")).build();
        JudgeRequest withSameQuestion = builder.context(JudgeContext.from("Where is my booking?")).build();
        JudgeRequest withConversation = builder.context(JudgeContext.from(List.of(UserMessage.from("Where is my booking?"))))
                .build();

        assertThat(withQuestion).isEqualTo(withSameQuestion).hasSameHashCodeAs(withSameQuestion);
        assertThat(withQuestion).isNotEqualTo(withConversation);
    }

}
