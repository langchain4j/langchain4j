package dev.langchain4j.model.vertexai;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.preview.GenerativeModel;
import com.google.cloud.vertexai.generativeai.preview.ResponseHandler;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.spi.VertexAiGeminiChatModelBuilderFactory;
import dev.langchain4j.spi.ServiceHelper;
import lombok.Builder;

import java.io.IOException;
import java.util.List;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a Google Vertex AI Gemini language model with a chat completion interface, such as gemini-pro.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini">here</a>.
 * <br>
 * Please follow these steps before using this model:
 * <br>
 * 1. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#authentication">Authentication</a>
 * <br>
 * When developing locally, you can use one of:
 * <br>
 * a) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#local-developmenttesting">Google Cloud SDK</a>
 * <br>
 * b) <a href="https://github.com/googleapis/google-cloud-java?tab=readme-ov-file#using-a-service-account-recommended">Service account</a>
 * When using service account, ensure that <code>GOOGLE_APPLICATION_CREDENTIALS</code> environment variable points to your JSON service account key.
 * <br>
 * 2. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#authorization">Authorization</a>
 * <br>
 * 3. <a href="https://github.com/googleapis/java-aiplatform?tab=readme-ov-file#prerequisites">Prerequisites</a>
 */
public class VertexAiGeminiChatModel implements ChatLanguageModel {

    private final GenerativeModel generativeModel;
    private final GenerationConfig generationConfig;
    private final Integer maxRetries;

    @Builder
    public VertexAiGeminiChatModel(String project,
                                   String location,
                                   String modelName,
                                   Float temperature,
                                   Integer maxOutputTokens,
                                   Integer topK,
                                   Float topP,
                                   Integer maxRetries) {

        try (VertexAI vertexAI = new VertexAI(
                ensureNotBlank(project, "project"),
                ensureNotBlank(location, "location"))
        ) {
            this.generativeModel = new GenerativeModel(ensureNotBlank(modelName, "modelName"), vertexAI);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.newBuilder();
        if (temperature != null) {
            generationConfigBuilder.setTemperature(temperature);
        }
        if (maxOutputTokens != null) {
            generationConfigBuilder.setMaxOutputTokens(maxOutputTokens);
        }
        if (topK != null) {
            generationConfigBuilder.setTopK(topK);
        }
        if (topP != null) {
            generationConfigBuilder.setTopP(topP);
        }
        this.generationConfig = generationConfigBuilder.build();

        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    public VertexAiGeminiChatModel(GenerativeModel generativeModel,
                                   GenerationConfig generationConfig) {
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel");
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
        this.maxRetries = 3;
    }

    public VertexAiGeminiChatModel(GenerativeModel generativeModel,
                                   GenerationConfig generationConfig,
                                   Integer maxRetries) {
        this.generativeModel = ensureNotNull(generativeModel, "generativeModel");
        this.generationConfig = ensureNotNull(generationConfig, "generationConfig");
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        List<Content> contents = ContentsMapper.map(messages);

        GenerateContentResponse response = withRetry(() -> generativeModel.generateContent(contents, generationConfig), maxRetries);

        return Response.from(
                AiMessage.from(ResponseHandler.getText(response)),
                TokenUsageMapper.map(response.getUsageMetadata()),
                FinishReasonMapper.map(ResponseHandler.getFinishReason(response))
        );
    }

    public static VertexAiGeminiChatModelBuilder builder() {
        return ServiceHelper.loadFactoryService(
                VertexAiGeminiChatModelBuilderFactory.class,
                VertexAiGeminiChatModelBuilder::new
        );
    }

    public static class VertexAiGeminiChatModelBuilder {
        public VertexAiGeminiChatModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }
    }
}
