package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.Collections.singletonList;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.CountTokensConfig;
import com.google.genai.types.CountTokensResponse;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.TokenCountEstimator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class GoogleGenAiTokenCountEstimator implements TokenCountEstimator {

    private final Client client;
    private final String modelName;
    private final Integer maxRetries;

    private GoogleGenAiTokenCountEstimator(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey,
                        builder.googleCredentials,
                        builder.projectId,
                        builder.location,
                        builder.timeout,
                        builder.customHeaders,
                        builder.apiEndpoint);
        this.modelName = ensureNotBlank(builder.modelName, "modelName");
        this.maxRetries = getOrDefault(builder.maxRetries, 3);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int estimateTokenCountInText(String text) {
        return estimateTokenCountInMessages(singletonList(UserMessage.from(text)));
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInMessages(singletonList(message));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        List<ChatMessage> allMessages = new LinkedList<>();
        messages.forEach(allMessages::add);

        List<Content> contents = GoogleGenAiContentMapper.toContents(allMessages);
        Content systemInstruction = GoogleGenAiContentMapper.toSystemInstruction(allMessages);

        if (systemInstruction != null) {
            // The Java SDK currently throws an exception if `systemInstruction` is passed to CountTokensConfig.
            // As a workaround, we simply append the system instruction as a standard Content block to approximate
            // tokens.
            List<Content> merged = new ArrayList<>();
            merged.add(systemInstruction);
            merged.addAll(contents);
            contents = merged;
        }

        if (contents.isEmpty()) {
            return 0;
        }

        return estimateTokenCount(contents, null);
    }

    public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
        List<ToolExecutionRequest> allToolRequests = new LinkedList<>();
        toolExecutionRequests.forEach(allToolRequests::add);

        return estimateTokenCountInMessage(AiMessage.from(allToolRequests));
    }

    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        List<FunctionDeclaration> functionDeclarations = new ArrayList<>();
        for (ToolSpecification toolSpec : toolSpecifications) {
            functionDeclarations.add(GoogleGenAiToolMapper.convertToGoogleFunction(toolSpec));
        }

        Tool tool = Tool.builder().functionDeclarations(functionDeclarations).build();

        // The Java SDK currently throws an exception if `tools` are passed to CountTokensConfig.
        // As a workaround, we serialize the tool declarations to a string and count the text tokens.
        String toolJson = tool.toJson();
        return estimateTokenCountInText(toolJson);
    }

    private int estimateTokenCount(List<Content> contents, CountTokensConfig config) {
        CountTokensResponse response =
                withRetryMappingExceptions(() -> client.models.countTokens(modelName, contents, config), maxRetries);
        return response.totalTokens().orElse(0);
    }

    public static class Builder {
        private Client client;
        private String apiKey;
        private GoogleCredentials googleCredentials;
        private String projectId;
        private String location;
        private Duration timeout;
        private String modelName;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private String apiEndpoint;
        private Map<String, String> customHeaders;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder googleCredentials(GoogleCredentials googleCredentials) {
            this.googleCredentials = googleCredentials;
            return this;
        }

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public GoogleGenAiTokenCountEstimator build() {
            return new GoogleGenAiTokenCountEstimator(this);
        }
    }
}
