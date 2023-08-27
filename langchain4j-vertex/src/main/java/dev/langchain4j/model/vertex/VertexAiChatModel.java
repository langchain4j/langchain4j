package dev.langchain4j.model.vertex;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.protobuf.Value.newBuilder;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.RetryUtils.withRetry;

/**
 * Represents a connection to the Vertex LLM with a chat completion interface, such as chat-bison.
 */
public class VertexAiChatModel implements ChatLanguageModel {

    private final String endpoint;
    private final String project;
    private final String location;
    private final String publisher;
    private final String modelName;
    private final VertexAiParameters vertexAiParameters;
    private final Integer maxRetries;

    VertexAiChatModel(String endpoint,
                      String project,
                      String location,
                      String publisher,
                      String modelName,
                      Double temperature,
                      Integer maxOutputTokens,
                      Integer topK,
                      Double topP,
                      Integer maxRetries) {

        this.endpoint = endpoint;
        this.project = project;
        this.location = location;
        this.publisher = publisher;
        this.modelName = modelName;
        this.vertexAiParameters = new VertexAiParameters(temperature, maxOutputTokens, topK, topP);
        this.maxRetries = maxRetries;
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages) {
        try {
            PredictionServiceSettings predictionServiceSettings = PredictionServiceSettings.newBuilder()
                    .setEndpoint(endpoint)
                    .build();

            PredictionServiceClient client = PredictionServiceClient.create(predictionServiceSettings);

            final EndpointName endpointName =
                    EndpointName.ofProjectLocationPublisherModelName(project, location, publisher, modelName);

            VertexAiInstance vertexAiInstance = new VertexAiInstance(toContext(messages), toVertexMessages(messages));

            Value.Builder instanceValue = newBuilder();
            JsonFormat.parser().merge(toJson(vertexAiInstance), instanceValue);
            List<Value> instances = Collections.singletonList(instanceValue.build());

            Value.Builder parameterValueBuilder = Value.newBuilder();
            JsonFormat.parser().merge(toJson(vertexAiParameters), parameterValueBuilder);
            Value parameterValue = parameterValueBuilder.build();

            PredictResponse response = withRetry(() -> client.predict(endpointName, instances, parameterValue), maxRetries);

            return aiMessage(extractContent(response));

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

    private static List<VertexAiInstance.Message> toVertexMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(chatMessage -> USER == chatMessage.type() || AI == chatMessage.type())
                .map(chatMessage -> new VertexAiInstance.Message(chatMessage.type().name(), chatMessage.text()))
                .collect(Collectors.toList());
    }

    private static String toContext(List<ChatMessage> messages) {
        return messages.stream()
                .filter(chatMessage -> SYSTEM == chatMessage.type())
                .map(ChatMessage::text)
                .collect(Collectors.joining(","));
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported for Vertex models");
    }

    @Override
    public AiMessage sendMessages(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new IllegalArgumentException("Tools are currently not supported for Vertex models");
    }

    public static Builder builder() {
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
