package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiDecisionModelIT extends AbstractDecisionModelIT {

    static final String MODEL_NAME = "gemini-decision-1";

    static final String RED_SQUARE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKElEQVR4nO3NsQ0AAAzCMP5/un0CNkuZ41wybXsHAAAAAAAAAAAAxR4yw/wuPL6QkAAAAABJRU5ErkJggg==";

    DecisionModel model = GoogleGenAiDecisionModel.builder()
            .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
            .modelName(MODEL_NAME)
            .build();

    @Override
    protected DecisionModel model() {
        return model;
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return GoogleGenAiDecisionModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName(MODEL_NAME)
                .listeners(listener)
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
        return MODEL_NAME;
    }

    @Test
    void should_decide_on_image() {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(
                        TextContent.from("A photo sent by the customer."),
                        ImageContent.from(RED_SQUARE_PNG, "image/png", ImageContent.DetailLevel.LOW)))
                .question("red", YesNoQuestion.from("Is the image mostly red?"))
                .question("blue", YesNoQuestion.from("Is the image mostly blue?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("red").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("blue").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_on_image_downloaded_by_gemini() {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(ImageContent.from(
                        "https://storage.googleapis.com/cloud-samples-data/generative-ai/image/scones.jpg")))
                .question("food", YesNoQuestion.from("Does the image show food?"))
                .question("car", YesNoQuestion.from("Does the image show a car?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("food").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("car").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_on_pdf() throws IOException {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(PdfFileContent.from(base64("invoice.pdf"), "application/pdf")))
                .question("document", ChoiceQuestion.from("What kind of document is it?", List.of("invoice", "letter")))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.choice("document").value()).isEqualTo("invoice");
    }

    @Test
    void should_decide_on_audio() throws IOException {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(AudioContent.from(base64("sample-voice.mp3"), "audio/mp3")))
                .question("speech", YesNoQuestion.from("Does a person speak in this recording?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("speech").probability()).isGreaterThan(0.5);
    }

    @Test
    void should_decide_on_conversation_with_tool_calls() {
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
                .question(
                        "charged_twice",
                        YesNoQuestion.from("According to the order data, was the customer charged twice?"))
                .question("refused", YesNoQuestion.from("Did the assistant refuse to help?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("tool").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("charged_twice").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("refused").probability()).isLessThan(0.5);
    }

    private static String base64(String resource) throws IOException {
        try (InputStream inputStream = GoogleGenAiDecisionModelIT.class.getResourceAsStream("/" + resource)) {
            return Base64.getEncoder().encodeToString(inputStream.readAllBytes());
        }
    }
}
