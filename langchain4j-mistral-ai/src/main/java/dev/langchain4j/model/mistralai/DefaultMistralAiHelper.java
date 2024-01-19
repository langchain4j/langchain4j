package dev.langchain4j.model.mistralai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import okhttp3.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.toList;

class DefaultMistralAiHelper{

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMistralAiHelper.class);
    static final String MISTRALAI_API_URL = "https://api.mistral.ai/v1";
    static final String MISTRALAI_API_CREATE_EMBEDDINGS_ENCODING_FORMAT = "float";
    private static final Pattern MISTRAI_API_KEY_BEARER_PATTERN = Pattern.compile("^(Bearer\\s*) ([A-Za-z0-9]{1,32})$");

    public static String ensureNotBlankApiKey(String value) {
        if (isNullOrBlank(value)) {
            throw new IllegalArgumentException("MistralAI API Key must be defined. It can be generated here: https://console.mistral.ai/user/api-keys/");
        }
        return value;
    }

    public static String formattedURLForRetrofit(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public static List<MistralChatMessage> toMistralAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(DefaultMistralAiHelper::toMistralAiMessage)
                .collect(toList());
    }

    public static MistralChatMessage toMistralAiMessage(ChatMessage message) {
        return MistralChatMessage.builder()
                .role(toMistralAiRole(message.type()))
                .content(message.text())
                .build();
    }

    private static MistralRoleName toMistralAiRole(ChatMessageType chatMessageType) {
        switch (chatMessageType) {
            case SYSTEM:
                return MistralRoleName.SYSTEM;
            case  AI:
                return MistralRoleName.ASSISTANT;
            case USER:
                return MistralRoleName.USER;
            default:
                throw new IllegalArgumentException("Unknown chat message type: " + chatMessageType);
        }

    }

    static TokenUsage tokenUsageFrom(MistralUsageInfo mistralAiUsage) {
        if (mistralAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                mistralAiUsage.getPromptTokens(),
                mistralAiUsage.getCompletionTokens(),
                mistralAiUsage.getTotalTokens()
        );
    }

    static FinishReason finishReasonFrom(String mistralAiFinishReason) {
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

    static String getHeaders(Headers headers){
       return StreamSupport.stream(headers.spliterator(),false).map(header -> {
           String headerKey = header.component1();
           String headerValue = header.component2();
           if (headerKey.equals("Authorization")) {
               headerValue = maskAuthorizationHeaderValue(headerValue);
           }
           return  String.format("[%s: %s]", headerKey, headerValue);
       }).collect(Collectors.joining(", "));
    }

    private static String maskAuthorizationHeaderValue(String authorizationHeaderValue) {
        try {
            Matcher matcher = MISTRAI_API_KEY_BEARER_PATTERN.matcher(authorizationHeaderValue);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String bearer = matcher.group(1);
                String token = matcher.group(2);
                matcher.appendReplacement(sb, bearer + " " + token.substring(0, 7) + "..." + token.substring(token.length() - 7));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return "Error while masking Authorization header value";
        }
    }

}
