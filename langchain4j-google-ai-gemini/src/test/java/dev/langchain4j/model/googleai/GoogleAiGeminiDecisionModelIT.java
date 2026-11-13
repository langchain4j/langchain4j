package dev.langchain4j.model.googleai;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.common.AbstractDecisionModelIT;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.DecisionRequestParameters;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleAiGeminiDecisionModelIT extends AbstractDecisionModelIT {

    private static final String MODEL_NAME = "gemini-decision-1";

    // a 32x32 red square
    private static final String RED_SQUARE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKElEQVR4nO3NsQ0AAAzCMP5/un0CNkuZ41wybXsHAAAAAAAAAAAAxR4yw/wuPL6QkAAAAABJRU5ErkJggg==";

    private static final String CAT_IMAGE_URL = "https://raw.githubusercontent.com/langchain4j/langchain4j/main/"
            + "langchain4j-core/src/test/resources/images/cat.jpg";

    private static final String AUDIO_URL =
            "https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3";

    static GoogleAiGeminiDecisionModel.GoogleAiGeminiDecisionModelBuilder builder() {
        return GoogleAiGeminiDecisionModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName(MODEL_NAME);
    }

    @Override
    protected DecisionModel model() {
        return builder().logRequests(true).logResponses(true).build();
    }

    private static DecisionModel modelWithoutRequestLogging() {
        return builder().logResponses(true).build(); // base64 media is huge in logs
    }

    @Override
    protected DecisionModel modelWithListener(DecisionModelListener listener) {
        return builder().listeners(listener).build();
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
    void should_map_unknown_model_to_model_not_found_exception() {

        DecisionRequest request = DecisionRequest.builder()
                .input("Hello")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .parameters(DecisionRequestParameters.builder()
                        .modelName("no-such-model-lc4j-it")
                        .build())
                .build();

        assertThatThrownBy(() -> model().decide(request)).isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void should_report_gemini_validation_message() {

        DecisionRequest request = DecisionRequest.builder()
                .input("Hello")
                .question(
                        "team",
                        ChoiceQuestion.builder()
                                .text("Which team should handle this?")
                                .option("a".repeat(129))
                                .option("support")
                                .build())
                .build();

        assertThatThrownBy(() -> model().decide(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("128");
    }

    @Test
    void should_decide_on_image_with_detail_level() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(
                        TextContent.from("A photo sent by the customer."),
                        ImageContent.from(RED_SQUARE_PNG, "image/png", ImageContent.DetailLevel.LOW)))
                .question("red", YesNoQuestion.from("Is the image mostly red?"))
                .question("blue", YesNoQuestion.from("Is the image mostly blue?"))
                .build();

        // when
        DecisionResponse response = model().decide(request);

        // then
        assertThat(response.yesNo("red").probability())
                .isGreaterThan(response.yesNo("blue").probability());
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
        DecisionResponse response = model().decide(request);

        // then
        assertThat(response.yesNo("cat").probability())
                .isGreaterThan(response.yesNo("dog").probability());
    }

    @Test
    void should_decide_on_audio() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(AudioContent.from(AUDIO_URL)))
                .question("phones", YesNoQuestion.from("Is the recording about smartphones?"))
                .question("cooking", YesNoQuestion.from("Is the recording about cooking?"))
                .build();

        // when
        DecisionResponse response = model().decide(request);

        // then
        assertThat(response.yesNo("phones").probability())
                .isGreaterThan(response.yesNo("cooking").probability());
    }

    @Test
    void should_decide_on_video() throws Exception {

        // given
        String base64Video =
                Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of("src/test/resources/example-video.mp4")));
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(VideoContent.from(base64Video, "video/mp4")))
                .question("video", YesNoQuestion.from("Does the input contain a video?"))
                .question("text_only", YesNoQuestion.from("Does the input contain only text?"))
                .build();

        // when
        DecisionResponse response = modelWithoutRequestLogging().decide(request);

        // then
        assertThat(response.yesNo("video").probability())
                .isGreaterThan(response.yesNo("text_only").probability());
    }

    @Test
    void should_decide_on_pdf() {

        // given
        String base64Pdf = Base64.getEncoder().encodeToString(pdf("Invoice 42. Total: 99 EUR. Status: paid."));
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(
                        TextContent.from("The customer attached this document."),
                        PdfFileContent.from(base64Pdf, "application/pdf")))
                .question("invoice", YesNoQuestion.from("Is the document an invoice?"))
                .question("recipe", YesNoQuestion.from("Is the document a cooking recipe?"))
                .build();

        // when
        DecisionResponse response = model().decide(request);

        // then
        assertThat(response.yesNo("invoice").probability())
                .isGreaterThan(response.yesNo("recipe").probability());
    }

    @Test
    void should_decide_on_conversation_with_image_and_tool_calls() {

        // given
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getOrder")
                .arguments("{\"orderId\": 42}")
                .build();
        DecisionRequest request = DecisionRequest.builder()
                .inputMessages(List.of(
                        SystemMessage.from("You are a support agent of an online shop."),
                        UserMessage.from(
                                TextContent.from("My order 42 arrived in the wrong color, here is a photo."),
                                ImageContent.from(RED_SQUARE_PNG, "image/png")),
                        AiMessage.builder()
                                .text("Let me look up your order.")
                                .toolExecutionRequests(List.of(toolCall))
                                .build(),
                        ToolExecutionResultMessage.from(
                                toolCall, "{\"orderId\": 42, \"color\": \"blue\", \"status\": \"delivered\"}"),
                        AiMessage.from("You ordered blue. I will send you a blue one.")))
                .question("tool", YesNoQuestion.from("Did the assistant look up the order with a tool?"))
                .question("red_photo", YesNoQuestion.from("Is the item in the photo sent by the user mostly red?"))
                .question("refused", YesNoQuestion.from("Did the assistant refuse to help?"))
                .build();

        // when
        DecisionResponse response = model().decide(request);

        // then
        assertThat(response.yesNo("tool").probability())
                .isGreaterThan(response.yesNo("refused").probability());
        assertThat(response.yesNo("red_photo").probability())
                .isGreaterThan(response.yesNo("refused").probability());
    }

    @Test
    void should_decide_on_fields_with_image() {

        // given
        DecisionRequest request = DecisionRequest.builder()
                .inputFields(Map.of(
                        "comment", "The package arrived like this.",
                        "photo", ImageContent.from(RED_SQUARE_PNG, "image/png")))
                .question("red", YesNoQuestion.from("Is the photo mostly red?"))
                .question("blue", YesNoQuestion.from("Is the photo mostly blue?"))
                .build();

        // when
        DecisionResponse response = model().decide(request);

        // then
        assertThat(response.yesNo("red").probability())
                .isGreaterThan(response.yesNo("blue").probability());
    }

    /**
     * A one-page PDF with the given ASCII text.
     */
    private static byte[] pdf(String text) {
        String content = "BT /F1 12 Tf 20 50 Td (" + text + ") Tj ET";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 100] /Contents 4 0 R "
                        + "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        offsets.forEach(offset -> pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset)));
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(ISO_8859_1);
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_GOOGLE_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
