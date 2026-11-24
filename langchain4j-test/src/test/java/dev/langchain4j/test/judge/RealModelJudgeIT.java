package dev.langchain4j.test.judge;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_1_MINI_2025_04_14;
import static dev.langchain4j.model.openai.OpenAiDecisionModelName.GPT_6_LUNA;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiDecisionModel;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The judges with real models: one obvious pass and one obvious fail per judge, checked by structure only (never by
 * the wording of the reasoning). They call paid APIs, so they run only when OPENAI_API_KEY is set.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RealModelJudgeIT {

    static final String QUESTION = "Can I cancel booking MS-777?";
    static final String ANSWER = "Yes. Booking MS-777 is now cancelled, and you will get a full refund of 120 EUR.";

    static Stream<Judge> judges() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_1_MINI_2025_04_14)
                .temperature(0.0)
                .build();
        DecisionModel decisionModel = OpenAiDecisionModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_6_LUNA)
                .build();
        return Stream.of(Judge.from(chatModel), Judge.from(decisionModel));
    }

    @ParameterizedTest
    @MethodSource("judges")
    void should_pass_an_obviously_satisfied_criterion_and_fail_an_obviously_unsatisfied_one(Judge judge) {
        List<JudgeVerdict> verdicts = judge.evaluate(JudgeRequest.builder()
                .text(ANSWER)
                .context(JudgeContext.from(QUESTION))
                .criteria(
                        JudgeCriterion.from("says that the booking is cancelled"),
                        JudgeCriterion.from("is written in German"))
                .build())
                .verdicts();

        assertThat(verdicts).hasSize(2);
        assertThat(verdicts.get(0).criterion().text()).isEqualTo("says that the booking is cancelled");
        assertThat(verdicts.get(0).isSatisfied()).isTrue();
        assertThat(verdicts.get(1).isSatisfied()).isFalse();
        assertThat(verdicts).allSatisfy(verdict -> assertThat(verdict.explanation()).isNotBlank());
    }

    @ParameterizedTest
    @MethodSource("judges")
    void should_judge_a_text_against_the_conversation_it_answers(Judge judge) {
        List<ChatMessage> conversation = List.of(
                SystemMessage.from("You are the support agent of a car rental company."),
                UserMessage.from(QUESTION),
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("cancelBooking")
                        .arguments("{\"bookingNumber\":\"MS-777\"}")
                        .build()),
                ToolExecutionResultMessage.from("1", "cancelBooking", "Booking MS-777 cancelled, refund: 120 EUR"));

        List<JudgeVerdict> verdicts = judge.evaluate(JudgeRequest.builder()
                .text(ANSWER)
                .context(JudgeContext.from(conversation))
                .criteria(
                        JudgeCriterion.from("states the refund amount returned by the cancellation tool"),
                        JudgeCriterion.from("says that the booking could not be found"))
                .build())
                .verdicts();

        assertThat(verdicts).hasSize(2);
        assertThat(verdicts.get(0).isSatisfied()).isTrue();
        assertThat(verdicts.get(1).isSatisfied()).isFalse();
    }
}
