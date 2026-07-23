package dev.langchain4j.model.bedrock;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.bedrock.Utils.extractAndValidateFormat;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.Internal;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps between LangChain4j chat types and the Bedrock batch JSONL payloads.
 *
 * <p>Batch jobs use the {@code Converse} invocation type, so {@code modelInput} is a Converse request body
 * (without {@code modelId}, which is set at the job level) and {@code modelOutput} is a Converse response body.
 * The payloads are built as plain maps and parsed with Jackson, mirroring how the embedding models build their
 * request bodies, rather than sending a request through the runtime client.</p>
 */
@Internal
final class BedrockBatchConverseMapper {

    private BedrockBatchConverseMapper() {}

    /**
     * Builds the {@code modelInput} (a Converse request body) for one chat request. Tool calling and structured
     * output are not supported by Bedrock batch inference and are rejected before this point.
     */
    static Map<String, Object> toModelInput(ChatRequest chatRequest) {
        Map<String, Object> modelInput = new LinkedHashMap<>();

        List<Map<String, Object>> messages = new ArrayList<>();
        List<Map<String, Object>> system = new ArrayList<>();
        for (ChatMessage message : chatRequest.messages()) {
            if (message instanceof SystemMessage systemMessage) {
                system.add(Map.of("text", systemMessage.text()));
            } else if (message instanceof UserMessage userMessage) {
                messages.add(message("user", contentBlocks(userMessage.contents())));
            } else if (message instanceof AiMessage aiMessage) {
                messages.add(message("assistant", List.of(Map.of("text", aiMessage.text()))));
            } else {
                throw new UnsupportedFeatureException(
                        message.type() + " messages are not supported by Bedrock batch inference");
            }
        }

        modelInput.put("messages", messages);
        if (!system.isEmpty()) {
            modelInput.put("system", system);
        }
        Map<String, Object> inferenceConfig = inferenceConfig(chatRequest.parameters());
        if (!inferenceConfig.isEmpty()) {
            modelInput.put("inferenceConfig", inferenceConfig);
        }

        return modelInput;
    }

    private static Map<String, Object> message(String role, List<Map<String, Object>> content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
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
                blocks.add(Map.of(
                        "document", Map.of("format", "pdf", "name", "document", "source", Map.of("bytes", bytes))));
            } else {
                throw new UnsupportedFeatureException(
                        content.type() + " content is not supported by Bedrock batch inference");
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

    /**
     * Parses one {@code modelOutput} JSON (a Converse response body) into a {@link ChatResponse}. Only text content
     * blocks are surfaced: tool-use blocks are rejected before submission, and any {@code reasoningContent}
     * (thinking) blocks a model may emit are intentionally ignored, since batch inference does not surface thinking.
     */
    static ChatResponse toChatResponse(JsonNode modelOutput, String modelName) {
        List<String> texts = new ArrayList<>();
        JsonNode content = modelOutput.path("output").path("message").path("content");
        for (JsonNode block : content) {
            if (block.hasNonNull("text")) {
                String value = block.get("text").asText();
                if (!value.isEmpty()) {
                    texts.add(value);
                }
            }
        }
        // Join like BedrockChatModel so batch and non-batch parse the same response identically.
        String text = String.join("\n\n", texts);

        JsonNode usage = modelOutput.path("usage");
        BedrockTokenUsage tokenUsage = BedrockTokenUsage.builder()
                .inputTokenCount(intOrNull(usage, "inputTokens"))
                .outputTokenCount(intOrNull(usage, "outputTokens"))
                .build();

        return ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .metadata(BedrockChatResponseMetadata.builder()
                        .finishReason(
                                finishReason(modelOutput.path("stopReason").asText(null)))
                        .tokenUsage(tokenUsage)
                        .modelName(modelName)
                        .build())
                .build();
    }

    private static FinishReason finishReason(String stopReason) {
        if (stopReason == null) {
            return null;
        }
        return switch (stopReason) {
            case "end_turn", "stop_sequence" -> FinishReason.STOP;
            case "max_tokens" -> FinishReason.LENGTH;
            case "content_filtered", "guardrail_intervened" -> FinishReason.CONTENT_FILTER;
            case "tool_use" -> FinishReason.TOOL_EXECUTION;
            default -> FinishReason.OTHER;
        };
    }

    private static String base64(String base64Data, java.net.URI url) {
        if (base64Data != null) {
            return base64Data;
        }
        return Base64.getEncoder().encodeToString(readBytes(String.valueOf(url)));
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }
}
