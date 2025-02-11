package dev.langchain4j.model.vertexai;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.vertexai.spi.VertexAiChatModelBuilderFactory;

import java.io.IOException;
import java.util.List;

import static com.google.protobuf.Value.newBuilder;
import static dev.langchain4j.data.message.ChatMessageType.*;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Represents a Google Vertex AI language model with a chat completion interface, such as chat-bison.
 * See details <a href="https://cloud.google.com/vertex-ai/docs/generative-ai/chat/chat-prompts">here</a>.
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
public class VertexAiChatModel implements ChatLanguageModel {

    private final PredictionServiceSettings settings;
    private final EndpointName endpointName;
    private final VertexAiParameters vertexAiParameters;
    private final Integer maxRetries;

    public VertexAiChatModel(String endpoint,
                             String project,
                             String location,
                             String publisher,
                             String modelName,
                             Double temperature,
                             Integer maxOutputTokens,
                             Integer topK,
                             Double topP,
                             Integer maxRetries) {
        try {
            this.settings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(ensureNotBlank(endpoint, "endpoint"))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.endpointName = EndpointName.ofProjectLocationPublisherModelName(
                ensureNotBlank(project, "project"),
                ensureNotBlank(location, "location"),
                ensureNotBlank(publisher, "publisher"),
                ensureNotBlank(modelName, "modelName")
        );
        this.vertexAiParameters = new VertexAiParameters(temperature, maxOutputTokens, topK, topP);
        this.maxRetries = maxRetries == null ? 3 : maxRetries;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        try (PredictionServiceClient client = PredictionServiceClient.create(settings)) {

            VertexAiChatInstance vertexAiChatInstance = new VertexAiChatInstance(
                    toContext(messages),
                    toVertexMessages(messages)
            );

            Value.Builder instanceBuilder = newBuilder();
            JsonFormat.parser().merge(toJson(vertexAiChatInstance), instanceBuilder);
            List<Value> instances = singletonList(instanceBuilder.build());

            Value.Builder parametersBuilder = newBuilder();
            JsonFormat.parser().merge(toJson(vertexAiParameters), parametersBuilder);
            Value parameters = parametersBuilder.build();

            PredictResponse response = withRetry(() -> client.predict(endpointName, instances, parameters), maxRetries);

            return Response.from(
                    AiMessage.from(extractContent(response)),
                    new TokenUsage(
                            extractTokenCount(response, "inputTokenCount"),
                            extractTokenCount(response, "outputTokenCount")
                    )
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractContent(PredictResponse predictResponse) {
        return predictResponse.getPredictions(0)
                .getStructValue()
                .getFieldsMap()
                .get("candidates")
                .getListValue()
                .getValues(0)
                .getStructValue()
                .getFieldsMap()
                .get("content")
                .getStringValue();
    }

    static int extractTokenCount(PredictResponse predictResponse, String fieldName) {
        return (int) predictResponse.getMetadata()
                .getStructValue()
                .getFieldsMap()
                .get("tokenMetadata")
                .getStructValue()
                .getFieldsMap()
                .get(fieldName)
                .getStructValue()
                .getFieldsMap()
                .get("totalTokens")
                .getNumberValue();
    }

    private static List<VertexAiChatInstance.Message> toVertexMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(chatMessage -> chatMessage.type() == USER || chatMessage.type() == AI)
                .map(chatMessage -> new VertexAiChatInstance.Message(chatMessage.type().name(), chatMessage.text()))
                .collect(toList());
    }

    private static String toContext(List<ChatMessage> messages) {
        return messages.stream()
                .filter(chatMessage -> chatMessage.type() == SYSTEM)
                .map(ChatMessage::text)
                .collect(joining("\n"));
    }

    public static Builder builder() {
        for (VertexAiChatModelBuilderFactory factory : loadFactories(VertexAiChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String project;
        private String location;
        private String publisher;
        private String modelName;

        private Double temperature;
        private Integer maxOutputTokens = 200;
        private Integer topK;
        private Double topP;

        private Integer maxRetries;

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder project(String project) {
            this.project = project;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder publisher(String publisher) {
            this.publisher = publisher;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public VertexAiChatModel build() {
            return new VertexAiChatModel(
                    endpoint,
                    project,
                    location,
                    publisher,
                    modelName,
                    temperature,
                    maxOutputTokens,
                    topK,
                    topP,
                    maxRetries);
        }
    }
}
