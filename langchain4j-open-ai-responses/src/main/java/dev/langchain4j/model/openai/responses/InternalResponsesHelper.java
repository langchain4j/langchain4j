package dev.langchain4j.model.openai.responses;

import com.openai.core.JsonValue;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponsesModel;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for converting between langchain4j and OpenAI SDK Responses API objects.
 */
class InternalResponsesHelper {

    /**
     * Converts a langchain4j ChatRequest to OpenAI SDK ResponseCreateParams.
     */
    static ResponseCreateParams.Builder toResponseCreateParams(
            ChatRequest chatRequest, OpenAiResponsesChatRequestParameters parameters) {

        ResponseCreateParams.Builder builder = ResponseCreateParams.builder();

        // Set model
        if (parameters.modelName() != null) {
            builder.model(parameters.modelName()); // String overload
        }

        // Build input items list
        List<ResponseInputItem> inputItems = new ArrayList<>();

        // Add ALL non-message items from previous outputs (reasoning, tool calls, etc.)
        // Messages are handled separately via chatRequest.messages() to maintain full conversation history
        if (parameters.previousOutputItems() != null
                && !parameters.previousOutputItems().isEmpty()) {
            parameters.previousOutputItems().forEach(outputItem -> {
                // Extract and preserve all non-message items (reasoning, tool calls, etc.)
                if (outputItem.isReasoning()) {
                    // Reasoning items (with content, summary, or encrypted_content)
                    inputItems.add(ResponseInputItem.ofReasoning(outputItem.asReasoning()));
                } else if (outputItem.isFunctionCall()) {
                    // Function/tool calls
                    inputItems.add(ResponseInputItem.ofFunctionCall(outputItem.asFunctionCall()));
                } else if (outputItem.isFileSearchCall()) {
                    // File search tool calls
                    inputItems.add(ResponseInputItem.ofFileSearchCall(outputItem.asFileSearchCall()));
                } else if (outputItem.isComputerCall()) {
                    // Computer use tool calls
                    inputItems.add(ResponseInputItem.ofComputerCall(outputItem.asComputerCall()));
                } else if (outputItem.isWebSearchCall()) {
                    // Web search tool calls
                    inputItems.add(ResponseInputItem.ofWebSearchCall(outputItem.asWebSearchCall()));
                } else if (outputItem.isCodeInterpreterCall()) {
                    // Code interpreter tool calls
                    inputItems.add(ResponseInputItem.ofCodeInterpreterCall(outputItem.asCodeInterpreterCall()));
                } else if (outputItem.isCustomToolCall()) {
                    // Custom tool calls
                    inputItems.add(ResponseInputItem.ofCustomToolCall(outputItem.asCustomToolCall()));
                }
                // Note: LocalShellCall, McpCall, McpListTools, McpApprovalRequest, ImageGenerationCall
                // use inner class types that are not directly compatible between ResponseOutputItem and
                // ResponseInputItem.
                // These would need explicit conversion logic if needed in the future.

                // Skip message items - those are maintained via chatRequest.messages() for full conversation history
            });
        }

        // Add ALL messages from current request (full conversation history)
        chatRequest.messages().forEach(message -> inputItems.add(convertChatMessageToInputItem(message)));

        // Use inputOfResponse for structured input (supports encrypted reasoning)
        builder.inputOfResponse(inputItems);

        // Responses API specific parameters
        if (parameters.store() != null) {
            builder.store(parameters.store());
        }

        if (parameters.include() != null && !parameters.include().isEmpty()) {
            // Convert List<String> to List<ResponseIncludable>
            List<ResponseIncludable> includables =
                    parameters.include().stream().map(ResponseIncludable::of).collect(Collectors.toList());
            builder.include(includables);
        }

        if (parameters.instructions() != null) {
            builder.instructions(parameters.instructions());
        }

        // Set reasoning parameter if reasoningEffort is provided
        // This tells the API how to reason on THIS request
        if (parameters.reasoningEffort() != null) {
            ReasoningEffort effort = ReasoningEffort.of(parameters.reasoningEffort());
            builder.reasoning(Reasoning.builder()
                    .effort(effort)
                    .summary(Reasoning.Summary.AUTO) // Compatible with encrypted_content
                    .build());
        }

        if (parameters.metadata() != null) {
            // Convert Map<String, String> to ResponseCreateParams.Metadata
            ResponseCreateParams.Metadata.Builder metadataBuilder = ResponseCreateParams.Metadata.builder();
            parameters
                    .metadata()
                    .forEach((key, value) -> metadataBuilder.putAdditionalProperty(key, JsonValue.from(value)));
            builder.metadata(metadataBuilder.build());
        }

        // Standard chat parameters
        if (parameters.temperature() != null) {
            builder.temperature(parameters.temperature());
        }

        if (parameters.topP() != null) {
            builder.topP(parameters.topP());
        }

        if (parameters.maxCompletionTokens() != null) {
            builder.maxOutputTokens(parameters.maxCompletionTokens().longValue());
        }

        // Note: seed is not supported in Responses API

        if (parameters.user() != null) {
            builder.user(parameters.user());
        }

        // Tool specifications (if supported by Responses API)
        if (chatRequest.toolSpecifications() != null
                && !chatRequest.toolSpecifications().isEmpty()) {
            // For POC, we'll skip tools implementation
            // Full implementation would convert ToolSpecification to ResponseCreateParams tools
        }

        return builder;
    }

    /**
     * Converts a langchain4j ChatMessage to OpenAI SDK ResponseInputItem.
     */
    private static ResponseInputItem convertChatMessageToInputItem(ChatMessage message) {
        EasyInputMessage.Role role;
        String content;

        switch (message.type()) {
            case SYSTEM:
                role = EasyInputMessage.Role.SYSTEM;
                content = ((dev.langchain4j.data.message.SystemMessage) message).text();
                break;
            case USER:
                role = EasyInputMessage.Role.USER;
                content = ((dev.langchain4j.data.message.UserMessage) message).singleText();
                break;
            case AI:
                role = EasyInputMessage.Role.ASSISTANT;
                content = ((dev.langchain4j.data.message.AiMessage) message).text();
                break;
            case TOOL_EXECUTION_RESULT:
                // For POC, treat as user message
                role = EasyInputMessage.Role.USER;
                content = ((dev.langchain4j.data.message.ToolExecutionResultMessage) message).text();
                break;
            default:
                role = EasyInputMessage.Role.USER;
                content = message.toString();
        }

        return ResponseInputItem.ofEasyInputMessage(
                EasyInputMessage.builder().role(role).content(content).build());
    }

    /**
     * Converts OpenAI SDK Response to langchain4j ChatResponse.
     */
    static ChatResponse toChatResponse(Response response) {
        // Extract the main message content from the response
        String content = extractContent(response);

        AiMessage aiMessage = AiMessage.from(content);

        // Build metadata
        ResponsesChatResponseMetadata.Builder metadataBuilder =
                ResponsesChatResponseMetadata.builder().id(response.id()).modelName(extractModelName(response.model()));

        // Extract token usage if available
        response.usage().ifPresent(usage -> {
            TokenUsage tokenUsage = new TokenUsage((int) usage.inputTokens(), (int) usage.outputTokens());
            metadataBuilder.tokenUsage(tokenUsage);
        });

        // Extract created timestamp (Double in seconds)
        metadataBuilder.created((long) (response.createdAt() * 1000)); // Convert to milliseconds

        // Store raw output items (for encrypted reasoning chaining)
        List<ResponseOutputItem> outputItems = response.output();
        if (outputItems != null && !outputItems.isEmpty()) {
            metadataBuilder.outputItems(outputItems);
        }

        // Extract reasoning items (key POC feature!)
        List<Map<String, Object>> reasoningItems = extractReasoningItems(response);
        if (!reasoningItems.isEmpty()) {
            metadataBuilder.reasoningItems(reasoningItems);
        }

        // Determine finish reason
        FinishReason finishReason = determineFinishReason(response);
        if (finishReason != null) {
            metadataBuilder.finishReason(finishReason);
        }

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(metadataBuilder.build())
                .build();
    }

    private static String extractContent(Response response) {
        // Extract message content from ResponseOutputItem objects
        List<ResponseOutputItem> outputs = response.output();
        if (outputs != null && !outputs.isEmpty()) {
            // Find the first message item and extract its content
            for (ResponseOutputItem item : outputs) {
                if (item.isMessage()) {
                    return item.asMessage().content().stream()
                            .findFirst()
                            .map(content -> {
                                if (content.isOutputText()) {
                                    return content.asOutputText().text();
                                } else if (content.isRefusal()) {
                                    return content.asRefusal().refusal();
                                }
                                return "";
                            })
                            .orElse("");
                }
            }
        }
        return "";
    }

    private static List<Map<String, Object>> extractReasoningItems(Response response) {
        List<Map<String, Object>> reasoningItems = new ArrayList<>();

        // Extract reasoning items from ResponseOutputItem objects
        List<ResponseOutputItem> outputs = response.output();
        if (outputs != null) {
            for (ResponseOutputItem item : outputs) {
                if (item.isReasoning()) {
                    Map<String, Object> reasoning = new HashMap<>();
                    reasoning.put("type", "reasoning");
                    reasoning.put("id", item.asReasoning().id());

                    // Extract encrypted content if present
                    item.asReasoning()
                            .encryptedContent()
                            .ifPresent(encrypted -> reasoning.put("encrypted_content", encrypted));

                    // Extract summary text if present
                    if (!item.asReasoning().summary().isEmpty()) {
                        // Extract text from each Summary object
                        List<String> summaryTexts = item.asReasoning().summary().stream()
                                .map(summary -> summary.text())
                                .collect(Collectors.toList());
                        reasoning.put("summary", summaryTexts);
                    }

                    // Extract content text if present (full reasoning text)
                    if (!item.asReasoning().content().isEmpty()) {
                        List<String> contentTexts = item.asReasoning().content().stream()
                                .map(content -> content.toString())
                                .collect(Collectors.toList());
                        reasoning.put("content", contentTexts);
                    }

                    reasoningItems.add(reasoning);
                }
            }
        }

        return reasoningItems;
    }

    private static FinishReason determineFinishReason(Response response) {
        // Extract finish reason from response
        // For POC, defaulting to STOP
        return FinishReason.STOP;
    }

    private static String extractModelName(ResponsesModel model) {
        // ResponsesModel is a union type - extract the string representation
        if (model.isChat()) {
            return model.asChat().toString();
        } else if (model.isString()) {
            return model.asString();
        } else if (model.isOnly()) {
            return model.asOnly().toString();
        }
        return model.toString(); // Fallback
    }
}
