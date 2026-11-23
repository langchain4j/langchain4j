package dev.langchain4j.model.onnx.genai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag("integration")
public class OnnxGenaiChatModelIT {

    private static final Logger logger = LoggerFactory.getLogger(OnnxGenaiChatModelIT.class);
    private static String TEST_MODEL_DIR;
    private static OnnxGenaiChatModel model;

    @BeforeAll
    static void setUp() {
        try {
            // Download model from Hugging Face if not already present
            TEST_MODEL_DIR = ModelDownloadUtil.ensureModelDownloaded();

            // Create model with default parameters
            model = OnnxGenaiChatModel.withDefaultParameters(TEST_MODEL_DIR);
            logger.info("Initialized test model from {}", TEST_MODEL_DIR);
        } catch (Exception e) {
            logger.error("Failed to initialize test model", e);
            throw new RuntimeException("Failed to initialize test model", e);
        }
    }

    @AfterAll
    static void tearDown() {
        if (model != null) {
            try {
                model.close();
                logger.info("Closed test model");
            } catch (Exception e) {
                logger.error("Failed to close test model", e);
            }
        }
    }

    @Test
    void should_chat_with_default_parameters() {
        // Given
        SystemMessage systemMessage = SystemMessage.from(
                "You are a helpful assistant, extremely " + "knowledgeable about the world geography.");
        UserMessage userMessage = UserMessage.from("What is the capital of Italy?");
        List<ChatMessage> messages = List.of(systemMessage, userMessage);

        // When
        ChatResponse response = model.chat(messages);

        // Then
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotEmpty();
        // Note: We can't assert exact text since it depends on the model's response
        logger.info("Model response: {}", response.aiMessage().text());
    }

    @Test
    void should_chat_with_different_prompt_template() {
        // Given
        OnnxGenaiPromptTemplate customTemplate =
                new OnnxGenaiPromptTemplate("### System:\n", "### Human:\n", "### AI:\n", "", "", "\n");

        try (OnnxGenaiChatModel customModel = OnnxGenaiChatModel.builder()
                .modelPath(TEST_MODEL_DIR)
                .parameters(OnnxGenaiParameters.builder().build())
                .promptTemplate(customTemplate)
                .build()) {
            ChatRequest request = ChatRequest.builder()
                    .messages(
                            SystemMessage.from("Be concise and helpful. Possibly answer with just a single sentence."),
                            UserMessage.from("How do you say 'weather' in Italian?"))
                    .build();

            // When
            ChatResponse response = customModel.chat(request);

            // Then
            assertThat(response.aiMessage()).isNotNull();
            assertThat(response.aiMessage().text()).isNotEmpty();
            // Note: We can't assert exact text since it depends on the model's response
            logger.info(
                    "Model response with custom template: {}",
                    response.aiMessage().text());
        }
    }

    @Test
    void should_chat_with_custom_parameters() {
        // Given
        OnnxGenaiParameters customParams = OnnxGenaiParameters.builder()
                .maxTokens(50)
                .temperature(0.7f)
                .topP(0.9f)
                .topK(40)
                .repetitionPenalty(1.1f)
                .doSample(true)
                .build();

        try (OnnxGenaiChatModel customModel = OnnxGenaiChatModel.builder()
                .modelPath(TEST_MODEL_DIR)
                .parameters(customParams)
                .build(); ) {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from("You are a helpful assistant."),
                    UserMessage.from("Tell me a short story about a robot."));

            // When
            ChatResponse response = customModel.chat(messages);

            // Then
            assertThat(response.aiMessage()).isNotNull();
            assertThat(response.aiMessage().text()).isNotEmpty();
            logger.info(
                    "Model response with custom parameters: {}",
                    response.aiMessage().text());
        }
    }
}
