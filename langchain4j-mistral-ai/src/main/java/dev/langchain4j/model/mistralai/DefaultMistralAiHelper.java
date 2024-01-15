package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.toList;

public class DefaultMistralAiHelper{

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMistralAiHelper.class);
    static final String MISTRALAI_API_URL = "https://api.mistral.ai/v1";
    static final String MISTRALAI_API_CREATE_EMBEDDINGS_ENCODING_FORMAT = "float";

    public static String ensureNotBlankApiKey(String value) {
        if (isNullOrBlank(value)) {
            throw new IllegalArgumentException("MistralAI API Key must be defined. It can be generated here: https://console.mistral.ai/user/api-keys/");
        }
        return value;
    }

    public static String formattedURLForRetrofit(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public static List<dev.langchain4j.model.mistralai.ChatMessage> toMistralAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(DefaultMistralAiHelper::toMistralAiMessage)
                .collect(toList());
    }

    public static dev.langchain4j.model.mistralai.ChatMessage toMistralAiMessage(ChatMessage message) {
        return dev.langchain4j.model.mistralai.ChatMessage.builder()
                .role(toMistralAiRole(message.type()))
                .content(message.text())
                .build();
    }

    private static Role toMistralAiRole(ChatMessageType chatMessageType) {
        switch (chatMessageType) {
            case SYSTEM:
                return Role.SYSTEM;
            case  AI:
                return Role.ASSISTANT;
            case USER:
                return Role.USER;
            default:
                throw new IllegalArgumentException("Unknown chat message type: " + chatMessageType);
        }

    }

    public static TokenUsage tokenUsageFrom(UsageInfo mistralAiUsage) {
        if (mistralAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                mistralAiUsage.getPromptTokens(),
                mistralAiUsage.getCompletionTokens(),
                mistralAiUsage.getTotalTokens()
        );
    }

    public static FinishReason finishReasonFrom(String mistralAiFinishReason) {
        if (mistralAiFinishReason == null) {
            return null;
        }
        switch (mistralAiFinishReason) {
            case "stop":
                return STOP;
            case "length":
                return LENGTH;
            case "model_length":
            default:
                return null;
        }
    }

    public static void logResponse(Response response){
        try {
            LOGGER.debug("Response code: {}", response.code());
            LOGGER.debug("Response body: {}", getResponseBody(response));
            LOGGER.debug("Response headers: {}", getResponseHeaders(response));
        } catch (IOException e) {
            LOGGER.warn("Error while logging response", e);
        }
    }

    private static String getResponseBody(Response response) throws IOException {
       return isEventStream(response) ? "" : response.peekBody(Long.MAX_VALUE).string();
    }

    private static String getResponseHeaders(Response response){
       return (String) StreamSupport.stream(response.headers().spliterator(),false).map(header -> {
           String headerKey = header.component1();
           String headerValue = header.component2();
           if (headerKey.equals("Authorization")) {
               headerValue = "Bearer " + headerValue.substring(0, 5) + "..." + headerValue.substring(headerValue.length() - 5);
           } else if (headerKey.equals("api-key")) {
               headerValue = headerValue.substring(0, 2) + "..." + headerValue.substring(headerValue.length() - 2);
           }
           return  String.format("[%s: %s]", headerKey, headerValue);
       }).collect(Collectors.joining(", "));
    }
    private static boolean isEventStream(Response response){
        String contentType = response.header("Content-Type");
        return contentType != null && contentType.contains("event-stream");
    }

}
