package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.bedrock.AbstractBedrockChatModel.THINKING_SIGNATURE_KEY;
import static dev.langchain4j.model.bedrock.AbstractBedrockChatModel.extractFilenameWithoutExtensionFromUri;
import static dev.langchain4j.model.bedrock.Utils.extractAndValidateFormat;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@Internal
final class BedrockBatchConverseMapper {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
            .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
            .build());

    private BedrockBatchConverseMapper() {}

    static String toJsonLine(Object value) {
        return CODEC.toJson(value);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> fromJsonLine(String line) {
        return CODEC.fromJson(line, Map.class);
    }

    static Map<String, Object> toModelInput(
            ChatRequest chatRequest, Map<String, Object> additionalModelRequestFields, boolean sendThinking) {

        Map<String, Object> modelInput = new LinkedHashMap<>();

        List<Map<String, Object>> messages = new ArrayList<>();
        List<Map<String, Object>> system = new ArrayList<>();
        for (ChatMessage message : chatRequest.messages()) {
            if (message instanceof SystemMessage systemMessage) {
                system.add(Map.of("text", systemMessage.text()));
            } else if (message instanceof BedrockSystemMessage bedrockSystemMessage) {
                for (BedrockSystemContent content : bedrockSystemMessage.contents()) {
                    if (content instanceof BedrockSystemTextContent text) {
                        if (text.hasCachePoint()) {
                            throw new UnsupportedFeatureException(BedrockBatchChatModel.PROMPT_CACHING_NOT_SUPPORTED);
                        }
                        system.add(Map.of("text", text.text()));
                    }
                }
            } else if (message instanceof UserMessage userMessage) {
                messages.add(message("user", contentBlocks(userMessage.contents())));
            } else if (message instanceof AiMessage aiMessage) {
                messages.add(message("assistant", aiMessageBlocks(aiMessage, sendThinking)));
            } else if (message instanceof ToolExecutionResultMessage) {
                throw new UnsupportedFeatureException("Tool calling is not supported by Bedrock batch inference");
            } else {
                throw new UnsupportedFeatureException(
                        message.getClass().getSimpleName() + " is not supported by BedrockBatchChatModel");
            }
        }

        modelInput.put("messages", messages);
        if (!system.isEmpty()) {
            modelInput.put("system", system);
        }
        ChatRequestParameters parameters = chatRequest.parameters();
        Map<String, Object> inferenceConfig = inferenceConfig(parameters);
        if (!inferenceConfig.isEmpty()) {
            modelInput.put("inferenceConfig", inferenceConfig);
        }
        if (!isNullOrEmpty(additionalModelRequestFields)) {
            modelInput.put("additionalModelRequestFields", additionalModelRequestFields);
        }
        if (parameters instanceof BedrockChatRequestParameters bedrockParameters) {
            BedrockGuardrailConfiguration guardrail = bedrockParameters.bedrockGuardrailConfiguration();
            if (guardrail != null) {
                Map<String, Object> guardrailConfig = new LinkedHashMap<>();
                guardrailConfig.put("guardrailIdentifier", guardrail.guardrailIdentifier());
                guardrailConfig.put("guardrailVersion", guardrail.guardrailVersion());
                modelInput.put("guardrailConfig", guardrailConfig);
            }
            if (!isNullOrEmpty(bedrockParameters.requestMetadata())) {
                modelInput.put("requestMetadata", bedrockParameters.requestMetadata());
            }
        }

        return modelInput;
    }

    private static Map<String, Object> message(String role, List<Map<String, Object>> content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static List<Map<String, Object>> aiMessageBlocks(AiMessage aiMessage, boolean sendThinking) {
        if (aiMessage.hasToolExecutionRequests()) {
            throw new UnsupportedFeatureException("Tool calling is not supported by Bedrock batch inference");
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (sendThinking && aiMessage.thinking() != null) {
            Map<String, Object> reasoningText = new LinkedHashMap<>();
            reasoningText.put("text", aiMessage.thinking());
            reasoningText.put("signature", aiMessage.attribute(THINKING_SIGNATURE_KEY, String.class));
            blocks.add(Map.of("reasoningContent", Map.of("reasoningText", reasoningText)));
        }
        if (aiMessage.text() != null) {
            blocks.add(Map.of("text", aiMessage.text()));
        }
        return blocks;
    }

    private static List<Map<String, Object>> contentBlocks(List<Content> contents) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Content content : contents) {
            if (content instanceof TextContent text) {
                blocks.add(Map.of("text", text.text()));
            } else if (content instanceof ImageContent image) {
                String format = extractAndValidateFormat(image.image());
                String bytes = base64(image.image().base64Data(), image.image().url());
                blocks.add(Map.of("image", Map.of("format", format, "source", Map.of("bytes", bytes))));
            } else if (content instanceof PdfFileContent pdf) {
                String bytes = base64(pdf.pdfFile().base64Data(), pdf.pdfFile().url());
                String name =
                        extractFilenameWithoutExtensionFromUri(pdf.pdfFile().url());
                blocks.add(Map.of("document", Map.of("format", "pdf", "name", name, "source", Map.of("bytes", bytes))));
            } else {
                throw new UnsupportedFeatureException(
                        content.type() + " content is not supported by BedrockBatchChatModel");
            }
        }
        return blocks;
    }

    private static Map<String, Object> inferenceConfig(ChatRequestParameters parameters) {
        Map<String, Object> config = new LinkedHashMap<>();
        putIfNotNull(config, "maxTokens", parameters.maxOutputTokens());
        putIfNotNull(config, "temperature", parameters.temperature());
        putIfNotNull(config, "topP", parameters.topP());
        if (!isNullOrEmpty(parameters.stopSequences())) {
            config.put("stopSequences", parameters.stopSequences());
        }
        return config;
    }

    static ChatResponse toChatResponse(Map<String, Object> modelOutput, String modelName, boolean returnThinking) {
        List<String> texts = new ArrayList<>();
        String thinking = null;
        Map<String, Object> attributes = Map.of();
        for (Map<String, Object> block : maps(path(modelOutput, "output", "message"), "content")) {
            if (block.get("text") instanceof String text) {
                if (isNotNullOrEmpty(text)) {
                    texts.add(text);
                }
            } else if (returnThinking && block.get("reasoningContent") instanceof Map<?, ?> reasoningContent) {
                if (reasoningContent.get("reasoningText") instanceof Map<?, ?> reasoningText) {
                    if (reasoningText.get("text") instanceof String text && isNotNullOrEmpty(text)) {
                        thinking = text;
                    }
                    if (reasoningText.get("signature") instanceof String signature && isNotNullOrEmpty(signature)) {
                        attributes = Map.of(THINKING_SIGNATURE_KEY, signature);
                    }
                }
            }
        }
        String text = String.join("\n\n", texts);

        Map<String, Object> usage = map(modelOutput, "usage");
        BedrockTokenUsage tokenUsage = BedrockTokenUsage.builder()
                .inputTokenCount(integer(usage, "inputTokens"))
                .outputTokenCount(integer(usage, "outputTokens"))
                .build();

        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text(isNullOrEmpty(text) ? null : text)
                        .thinking(thinking)
                        .attributes(attributes)
                        .build())
                .metadata(BedrockChatResponseMetadata.builder()
                        .finishReason(finishReason(string(modelOutput, "stopReason")))
                        .tokenUsage(tokenUsage)
                        .modelName(modelName)
                        .build())
                .build();
    }

    private static @Nullable FinishReason finishReason(@Nullable String stopReason) {
        if (stopReason == null) {
            return null;
        }
        return switch (stopReason) {
            case "end_turn", "stop_sequence" -> FinishReason.STOP;
            case "max_tokens", "model_context_window_exceeded" -> FinishReason.LENGTH;
            case "tool_use" -> FinishReason.TOOL_EXECUTION;
            case "content_filtered", "guardrail_intervened" -> FinishReason.CONTENT_FILTER;
            default -> FinishReason.OTHER;
        };
    }

    static Map<String, Object> map(Map<String, Object> source, String key) {
        return source.get(key) instanceof Map<?, ?> value ? cast(value) : Map.of();
    }

    private static Map<String, Object> path(Map<String, Object> source, String... keys) {
        Map<String, Object> current = source;
        for (String key : keys) {
            current = map(current, key);
        }
        return current;
    }

    private static List<Map<String, Object>> maps(Map<String, Object> source, String key) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (source.get(key) instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> map) {
                    result.add(cast(map));
                }
            }
        }
        return result;
    }

    static @Nullable String string(Map<String, Object> source, String key) {
        return source.get(key) instanceof String value ? value : null;
    }

    static @Nullable Integer integer(Map<String, Object> source, String key) {
        return source.get(key) instanceof Number value ? value.intValue() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String base64(@Nullable String base64Data, @Nullable URI url) {
        if (base64Data != null) {
            return base64Data;
        }
        return Base64.getEncoder().encodeToString(readBytes(String.valueOf(url)));
    }

    private static void putIfNotNull(Map<String, Object> map, String key, @Nullable Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
