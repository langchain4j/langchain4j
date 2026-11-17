package dev.langchain4j.model.google.genai;

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
import dev.langchain4j.data.pdf.PdfFile;
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
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Choice;
import dev.langchain4j.service.decision.Decide;
import dev.langchain4j.service.decision.DecisionServices;
import dev.langchain4j.service.decision.Scale;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiDecisionModelIT extends AbstractDecisionModelIT {

    static final String MODEL_NAME = "gemini-decision-1";

    // a 32x32 red square
    static final String RED_SQUARE_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAKElEQVR4nO3NsQ0AAAzCMP5/un0CNkuZ41wybXsHAAAAAAAAAAAAxR4yw/wuPL6QkAAAAABJRU5ErkJggg==";

    static final String CAT_IMAGE_URL = "https://raw.githubusercontent.com/langchain4j/langchain4j/main/"
            + "langchain4j-core/src/test/resources/images/cat.jpg";

    static final String AUDIO_URL = "https://storage.googleapis.com/cloud-samples-data/generative-ai/audio/pixel.mp3";

    private static final String MINIMAL_PDF_BASE64 =
            Base64.getEncoder().encodeToString("""
                    %PDF-1.4
                    1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
                    2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
                    3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 300 144] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj
                    4 0 obj << /Length 55 >> stream
                    BT /F1 18 Tf 20 80 Td (Invoice #42 - Paid in full) Tj ET
                    endstream endobj
                    5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
                    xref
                    0 6
                    0000000000 65535 f
                    0000000009 00000 n
                    0000000058 00000 n
                    0000000115 00000 n
                    0000000241 00000 n
                    0000000347 00000 n
                    trailer << /Size 6 /Root 1 0 R >>
                    startxref
                    417
                    %%EOF
                    """.getBytes(StandardCharsets.US_ASCII));

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
    void should_map_unknown_model_to_model_not_found_exception() {
        DecisionRequest request = DecisionRequest.builder()
                .input("Hello")
                .question("spam", YesNoQuestion.from("Is this message spam?"))
                .parameters(DecisionRequestParameters.builder()
                        .modelName("no-such-model-lc4j-it")
                        .build())
                .build();

        assertThatThrownBy(() -> model.decide(request)).isInstanceOf(ModelNotFoundException.class);
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

        assertThatThrownBy(() -> model.decide(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("128");
    }

    @Test
    void should_return_google_genai_token_usage_and_metadata() {
        DecisionRequest request = DecisionRequest.builder()
                .input("Order #42 was delivered on Monday.")
                .question("$delivered", YesNoQuestion.from("Was the order delivered?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("$delivered").probability()).isGreaterThan(0.5);
        assertThat(response.metadata()).isInstanceOfSatisfying(GoogleGenAiDecisionResponseMetadata.class, metadata -> {
            assertThat(metadata.blockReason()).isNull();
            assertThat(metadata.blockMessage()).isNull();
        });
        assertThat(response.tokenUsage()).isInstanceOf(GoogleGenAiTokenUsage.class);
        assertThat(response.tokenUsage().inputTokenCount()).isPositive();
        assertThat(response.tokenUsage().totalTokenCount()).isPositive();
    }

    @Test
    void should_decide_on_inline_image() {
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
    void should_decide_on_image_referenced_by_url() {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(TextContent.from("An uploaded photograph."), ImageContent.from(CAT_IMAGE_URL)))
                .question("cat", YesNoQuestion.from("Is there a cat in the image?"))
                .question("car", YesNoQuestion.from("Is there a car in the image?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("cat").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("car").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_on_audio() {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(AudioContent.from(AUDIO_URL)))
                .question("phones", YesNoQuestion.from("Is the recording about smartphones?"))
                .question("cooking", YesNoQuestion.from("Is the recording about cooking?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("phones").probability())
                .isGreaterThan(response.yesNo("cooking").probability());
    }

    @Test
    void should_decide_on_pdf_document() {
        DecisionRequest request = DecisionRequest.builder()
                .inputContents(List.of(PdfFileContent.from(PdfFile.builder()
                        .base64Data(MINIMAL_PDF_BASE64)
                        .mimeType("application/pdf")
                        .build())))
                .question("paid", YesNoQuestion.from("Does this invoice state that it is paid in full?"))
                .question("overdue", YesNoQuestion.from("Does this invoice state that payment is overdue?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("paid").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("overdue").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_on_fields_with_image() {
        DecisionRequest request = DecisionRequest.builder()
                .inputFields(Map.of(
                        "comment",
                        "The package arrived like this.",
                        "photo",
                        ImageContent.from(RED_SQUARE_PNG, "image/png")))
                .question("red", YesNoQuestion.from("Is the photo mostly red?"))
                .question("blue", YesNoQuestion.from("Is the photo mostly blue?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("red").probability())
                .isGreaterThan(response.yesNo("blue").probability());
    }

    @Test
    void should_decide_on_conversation_with_image_and_tool_calls() {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("getOrder")
                .arguments("{\"orderId\": 42}")
                .build();
        DecisionRequest request = DecisionRequest.builder()
                .inputMessages(List.of(
                        SystemMessage.from("You are a billing support agent of an online shop."),
                        UserMessage.from(
                                TextContent.from("Hi, my order 42 arrived in the wrong color, here is a photo."),
                                ImageContent.from(RED_SQUARE_PNG, "image/png")),
                        AiMessage.builder()
                                .text("Sure, let me look it up.")
                                .toolExecutionRequests(List.of(toolCall))
                                .build(),
                        ToolExecutionResultMessage.from(
                                toolCall,
                                "{\"orderId\": 42, \"charges\": [19.99, 19.99], \"color\": \"blue\", \"status\": \"delivered\"}"),
                        AiMessage.from("You were charged twice. I have refunded one of the charges.")))
                .question("tool", YesNoQuestion.from("Did the assistant look up the order with a tool?"))
                .question(
                        "charged_twice",
                        YesNoQuestion.from("According to the order data, was the customer charged twice?"))
                .question("red_photo", YesNoQuestion.from("Is the item in the photo sent by the user mostly red?"))
                .question("refused", YesNoQuestion.from("Did the assistant refuse to help?"))
                .question(
                        "backed_by_tools",
                        YesNoQuestion.from("Did the assistant call a tool for every action it says it has taken?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("tool").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("charged_twice").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("red_photo").probability()).isGreaterThan(0.5);
        assertThat(response.yesNo("refused").probability()).isLessThan(0.5);
        assertThat(response.yesNo("backed_by_tools").probability()).isLessThan(0.5);
    }

    @Test
    void should_decide_on_tool_call_before_it_is_executed() {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("deleteAccount")
                .arguments("{\"userId\": 7}")
                .build();
        DecisionRequest request = DecisionRequest.builder()
                .inputMessages(
                        List.of(UserMessage.from("Can you check the status of my order 42?"), AiMessage.from(toolCall)))
                .question("requested", YesNoQuestion.from("Did the user ask for the action of the last tool call?"))
                .build();

        DecisionResponse response = model.decide(request);

        assertThat(response.yesNo("requested").probability()).isLessThan(0.5);
    }

    enum Team {
        @Description("Payments, payouts, invoices, and refunds")
        BILLING,
        @Description("Technical product bugs and general troubleshooting")
        SUPPORT
    }

    enum Frustration {
        @Description("Calm, neutral inquiry")
        CALM,
        @Description("Frustrated, blocked or impatient")
        FRUSTRATED,
        @Description("Hostile or threatening")
        HOSTILE
    }

    record Triage(
            @Decide("Does this ticket require urgent attention today?")
            boolean urgent,

            @Decide("Which team should handle this ticket?") Choice<Team> team,
            @Decide("How frustrated is the customer?") Scale<Frustration> frustration,

            @Decide("Is the attached screenshot mostly red?")
            boolean redScreenshot) {}

    interface SupportDesk {
        Triage triage(@V("ticket") String ticket, @V("screenshot") ImageContent screenshot);
    }

    @Test
    void should_work_with_declarative_decision_service() {
        SupportDesk supportDesk = DecisionServices.create(SupportDesk.class, model);

        Triage triage = supportDesk.triage(
                "My company's payouts have been failing for 3 days and payroll is due tomorrow!",
                ImageContent.from(RED_SQUARE_PNG, "image/png"));

        assertThat(triage.urgent()).isTrue();
        assertThat(triage.team().value()).isEqualTo(Team.BILLING);
        assertThat(triage.team().probabilityOf(Team.BILLING)).isGreaterThan(0.5);
        assertThat(triage.frustration().mean()).isGreaterThan(0.2);
        assertThat(triage.frustration().mean()).isLessThan(2.0);
        assertThat(triage.redScreenshot()).isTrue();
    }
}
