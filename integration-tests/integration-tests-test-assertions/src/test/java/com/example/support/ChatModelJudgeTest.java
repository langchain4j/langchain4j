package com.example.support;

import static com.example.support.Bookings.bookings;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.Result;
import dev.langchain4j.test.judge.Judge;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Judge assertions on what the AI Services of the application produced, with a chat model as the judge (mocked with
 * ChatModelMock). The judges themselves are tested in langchain4j-test.
 */
@Timeout(30)
class ChatModelJudgeTest {

    static final String ANSWER = "I could not find booking MS-999. Could you check the booking number?";

    /**
     * What the judge model answers: one result per condition, in order.
     */
    static String assessments(String... results) {
        return IntStream.range(0, results.length)
                .mapToObj(i -> "{\"conditionNumber\": %d, \"reasoning\": \"Reasoning %d.\", \"result\": \"%s\"}"
                        .formatted(i + 1, i + 1, results[i]))
                .collect(Collectors.joining(", ", "{\"assessments\": [", "]}"));
    }

    static String judgeUserMessage(ChatRequest request) {
        return ((UserMessage) request.messages().get(1)).singleText();
    }

    @Test
    void should_pass_when_the_judge_says_all_criteria_are_satisfied() {
        CustomerSupportAgent agent = CustomerSupport.customerSupportAgent(
                ChatModelMock.thatResponds(ANSWER),
                bookings(),
                CustomerSupport.policyExpert(ChatModelMock.thatResponds("unused")));
        ChatModelMock judgeModel = ChatModelMock.thatResponds(assessments("SATISFIED", "SATISFIED"));

        String answer = capture(() -> agent.chat("klaus", "Show MS-999")).outputText();

        Judge.from(judgeModel)
                .assertThat(answer)
                .given("Show MS-999")
                .satisfies("says that the booking cannot be found", "asks to check the booking number");

        assertThat(judgeModel.requests()).hasSize(1);
        assertThat(judgeUserMessage(judgeModel.request()))
                .contains("<user_message>\nShow MS-999\n</user_message>")
                .contains("<text>\n" + ANSWER + "\n</text>")
                .contains("<condition number=\"1\">says that the booking cannot be found</condition>")
                .contains("<condition number=\"2\">asks to check the booking number</condition>");
    }

    @Test
    void should_give_the_judge_the_sources_retrieved_by_the_ai_service() {
        PolicyExpert expert = CustomerSupport.policyExpert(
                ChatModelMock.thatResponds("You can cancel for free up to 7 days before the start date."));
        ChatModelMock judgeModel = ChatModelMock.thatResponds(assessments("SATISFIED"));

        Result<String> result = expert.answerWithSources("Can I cancel for free?");

        Judge.from(judgeModel)
                .assertThat(result.content())
                .basedOnContents(result.sources())
                .satisfies("contains only claims that are stated in the sources");

        assertThat(judgeUserMessage(judgeModel.request()))
                .contains("<sources>\n<source number=\"1\">\nCancellation: bookings can be cancelled free of charge");
    }}
