package dev.langchain4j.model.onnx.genai;

import static org.assertj.core.api.Assertions.assertThat;

import ai.onnxruntime.OrtEnvironment;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag("integration")
public class OnnxGenaiLanguageModelIT {

    private static final Logger logger = LoggerFactory.getLogger(OnnxGenaiLanguageModelIT.class);
    private static String TEST_MODEL_DIR;
    private static OnnxGenaiLanguageModel model;

    @BeforeAll
    static void setUp() {
        try {
            System.out.println("ONNX Runtime version: " + OrtEnvironment.getEnvironment());

            // Download model from Hugging Face if not already present
            TEST_MODEL_DIR = ModelDownloadUtil.ensureModelDownloaded();

            // Create model with default parameters
            model = OnnxGenaiLanguageModel.withDefaultParameters(TEST_MODEL_DIR);
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
    void should_generate_text_with_default_parameters() {
        // Given
        String prompt = "What is the capital of France?";

        // When
        Response<String> response = model.generate(prompt);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.content()).isNotEmpty();
        // Note: We can't assert exact text since it depends on the model's response
        logger.info("Model response: {}", response.content());
    }

    @Test
    void should_generate_text_with_custom_parameters() {
        // Given
        OnnxGenaiParameters customParams = OnnxGenaiParameters.builder()
                .maxTokens(50)
                .temperature(0.7f)
                .topP(0.9f)
                .topK(40)
                .repetitionPenalty(1.1f)
                .doSample(true)
                .build();

        try (OnnxGenaiLanguageModel customModel = OnnxGenaiLanguageModel.builder()
                .modelPath(TEST_MODEL_DIR)
                .parameters(customParams)
                .build()) {

            String prompt = "Write a short poem about artificial intelligence.";

            // When
            Response<String> response = customModel.generate(prompt);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.content()).isNotEmpty();
            logger.info("Model response with custom parameters: {}", response.content());
        }
    }

    @Test
    void should_build_model_with_builder() {
        // Given
        OnnxGenaiParameters params =
                OnnxGenaiParameters.builder().maxTokens(30).temperature(0.8f).build();

        // When
        try (OnnxGenaiLanguageModel builtModel = OnnxGenaiLanguageModel.builder()
                .modelPath(TEST_MODEL_DIR)
                .parameters(params)
                .build()) {

            // Then
            assertThat(builtModel).isNotNull();

            // Test the built model
            Response<String> response = builtModel.generate("What is 2+2?");
            assertThat(response).isNotNull();
            assertThat(response.content()).isNotEmpty();
            logger.info("Built model response: {}", response.content());
        }
    }
}
