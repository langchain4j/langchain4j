package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiDecisionModelName.GPT_6_LUNA;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiDecisionModelIT extends AbstractDecisionModelIT {

    // a 32x32 red square
    static final String RED_SQUARE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKElEQVR4nO3NsQ0AAAzCMP5/un0CNkuZ41wybXsHAAAAAAAAAAAAxR4yw/wuPL6QkAAAAABJRU5ErkJggg==";

    static final String CAT_IMAGE_URL = "https://raw.githubusercontent.com/langchain4j/langchain4j/main/"
            + "langchain4j-core/src/test/resources/images/cat.jpg";

    DecisionModel model = OpenAiDecisionModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_6_LUNA)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected DecisionModel model() {
        return model;
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return OpenAiDecisionModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_6_LUNA)
                .listeners(List.of(listener))
                .build();
    }

    @Override
    protected boolean reportsProbabilities() {
        return true;
    }

    @Override
    protected boolean reportsTokenUsage() {
        return true;
    }

    @Override
    protected String requestModelName() {
        return GPT_6_LUNA.toString();
    }

    @Test
    void should_decide_on_image() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(
                        TextContent.from("A photo sent by the customer."),
                        ImageContent.from(RED_SQUARE_PNG, "image/png", ImageContent.DetailLevel.LOW)))
                .question("red", YesNoQuestion.from("Is the image mostly red?"))
                .question("blue", YesNoQuestion.from("Is the image mostly blue?"))
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.yesNo("red").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("blue").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_on_image_referenced_by_url() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(ImageContent.from(CAT_IMAGE_URL)))
                .question("cat", YesNoQuestion.from("Is there a cat in the image?"))
                .question("dog", YesNoQuestion.from("Is there a dog in the image?"))
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.yesNo("cat").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("dog").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_on_conversation_with_tool_calls() {

        // given
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getOrder")
                .arguments("{\"orderId\": 42}")
                .build();
        DecisionRequest request = DecisionRequest.builder()
                .inputMessages(List.of(
                        SystemMessage.from("You are a billing support agent of an online shop."),
                        UserMessage.from("Hi, something is wrong with my order 42, can you check?"),
                        AiMessage.builder()
                                .text("Sure, let me look it up.")
                                .toolExecutionRequests(List.of(toolCall))
                                .build(),
                        ToolExecutionResultMessage.from(
                                toolCall, "{\"orderId\": 42, \"charges\": [19.99, 19.99], \"status\": \"delivered\"}"),
                        AiMessage.from("You were charged twice. I have refunded one of the charges.")))
                .question("tool", YesNoQuestion.from("Did the assistant look up the order with a tool?"))
                .question("charged_twice", YesNoQuestion.from("According to the order data, was the customer charged twice?"))
                .question("refused", YesNoQuestion.from("Did the assistant refuse to help?"))
                .question(
                        "backed_by_tools",
                        YesNoQuestion.from("Did the assistant call a tool for every action it says it has taken?"))
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.yesNo("tool").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("charged_twice").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("refused").probability()).isLessThan(0.5);
        assertThat(response.yesNo("backed_by_tools").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_on_tool_call_before_it_is_executed() {

        // given
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("deleteAccount")
                .arguments("{\"userId\": 7}")
                .build();
        DecisionRequest request = DecisionRequest.builder()
                .inputMessages(List.of(UserMessage.from("Can you check the status of my order 42?"), AiMessage.from(toolCall)))
                .question("requested", YesNoQuestion.from("Did the user ask for the action of the last tool call?"))
                .build();

        // when
        DecisionResponse response = model.decide(request);

        // then
        assertThat(response.yesNo("requested").probability()).isLessThan(0.5);
    }
}
