package dev.langchain4j.model.jitllm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.JsonSchemaElementUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.ToolSpec;

/**
 * Pure conversions between LangChain4j's vocabulary and the engine's public API.
 *
 * <p>Extracted from {@link JitLLMBaseModel} so the mapping can be tested <b>without a model</b>.
 * What a 1B model chooses to emit is its own business; whether a tool call is transported and mapped
 * correctly is this adapter's, and the two should not be provable only together. Every method here
 * is a function of its arguments — no engine, no device, no I/O.
 *
 * <p>Package-private on purpose. This is a test seam, not API: nothing here is published and there is
 * no mocking surface.
 */
final class JitLLMConversions {

    private JitLLMConversions() {}

    /**
     * The conversation, in the engine's vocabulary.
     *
     * <p>A straight translation. What used to live here — where tool JSON goes, which stop-token set
     * applies, how a family opens a turn — is the engine's, and this adapter no longer has an opinion
     * about any of it.
     */
    static List<org.beehive.jitllm.api.ChatMessage> toEngineMessages(List<ChatMessage> messages) {
        List<org.beehive.jitllm.api.ChatMessage> converted = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage user) {
                converted.add(org.beehive.jitllm.api.ChatMessage.of(ChatRole.USER, user.singleText()));
            } else if (message instanceof SystemMessage system) {
                converted.add(org.beehive.jitllm.api.ChatMessage.of(ChatRole.SYSTEM, system.text()));
            } else if (message instanceof AiMessage ai) {
                converted.add(toAssistantMessage(ai));
            } else if (message instanceof ToolExecutionResultMessage toolResult) {
                converted.add(new org.beehive.jitllm.api.ChatMessage(
                        ChatRole.TOOL,
                        List.of(new ChatContent.ToolResult(
                                blankToGenerated(toolResult.id()),
                                toolResult.toolName(),
                                unwrapToolResult(toolResult.text())))));
            }
        }
        return converted;
    }

    static org.beehive.jitllm.api.ChatMessage toAssistantMessage(AiMessage ai) {
        List<ChatContent> content = new ArrayList<>();
        if (ai.text() != null && !ai.text().isEmpty()) {
            content.add(new ChatContent.Text(ai.text()));
        }
        if (ai.hasToolExecutionRequests()) {
            for (ToolExecutionRequest tool : ai.toolExecutionRequests()) {
                content.add(new ChatContent.ToolCall(
                        blankToGenerated(tool.id()),
                        tool.name(),
                        tool.arguments() == null || tool.arguments().isBlank() ? "{}" : tool.arguments()));
            }
        }
        if (content.isEmpty()) {
            // An assistant turn with neither text nor calls still has to say something to the
            // template; an empty string is what it used to encode to.
            content.add(new ChatContent.Text(""));
        }
        return new org.beehive.jitllm.api.ChatMessage(ChatRole.ASSISTANT, content);
    }

    /**
     * The tool specifications, in the engine's vocabulary.
     *
     * <p>The parameter schema is serialized with {@code JsonSchemaElementUtils.toMap} through
     * {@code Json.toJson}, the form the GPULlama3 integration used. What a model does with a tool
     * definition is sensitive to its exact text, and this is the text that has been producing correct
     * calls.
     */
    static List<ToolSpec> toEngineTools(List<ToolSpecification> tools) {
        List<ToolSpec> specs = new ArrayList<>(tools.size());
        for (ToolSpecification tool : tools) {
            Map<String, Object> parameters = tool.parameters() == null
                    ? Map.of("type", "object", "properties", Map.of())
                    : JsonSchemaElementUtils.toMap(tool.parameters());
            specs.add(new ToolSpec(
                    tool.name(), tool.description() == null ? "" : tool.description(), Json.toJson(parameters)));
        }
        return specs;
    }

    /**
     * The engine's stop reason, in LangChain4j's vocabulary.
     *
     * <p>{@code TOOL_CALL} is the engine's narrow reason: it is reported only when a valid call was
     * extracted <b>and</b> generation ended through the format's tool-call termination path. It maps
     * to {@code TOOL_EXECUTION}, which is what a LangChain4j caller acts on.
     *
     * <p>Every other reason maps to {@code STOP} or {@code LENGTH}. Note what is deliberately not
     * here: extracted calls do <b>not</b> override the reason. A response that ran out of budget
     * mid-call ends with {@code LENGTH}, because that is what happened, and telling a caller to
     * execute a call the model had not finished writing is worse than telling them it was truncated.
     */
    static dev.langchain4j.model.output.FinishReason toLangChain4jFinishReason(
            org.beehive.jitllm.api.FinishReason engineReason) {
        return switch (engineReason) {
            case TOOL_CALL -> dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
            case MAX_TOKENS, CONTEXT_FULL -> dev.langchain4j.model.output.FinishReason.LENGTH;
            case STOP_TOKEN, STOP_SEQUENCE -> dev.langchain4j.model.output.FinishReason.STOP;
            // Cancelled through a CancellationToken; LangChain4j has no reason of its own for it.
            case CANCELLED -> dev.langchain4j.model.output.FinishReason.OTHER;
        };
    }

    /** The engine's tool calls, as LangChain4j execution requests — order preserved. */
    static List<ToolExecutionRequest> toToolExecutionRequests(List<ChatContent.ToolCall> calls) {
        List<ToolExecutionRequest> requests = new ArrayList<>(calls.size());
        for (ChatContent.ToolCall call : calls) {
            requests.add(ToolExecutionRequest.builder()
                    .id(call.id())
                    .name(call.name())
                    .arguments(normalizeJson(call.argumentsJson()))
                    .build());
        }
        return requests;
    }

    /**
     * A tool result's text, unwrapped when the framework handed us a JSON string literal.
     */
    static String unwrapToolResult(String text) {
        if (text == null) {
            return "";
        }
        if (text.startsWith("\"")) {
            try {
                return Json.fromJson(text, String.class);
            } catch (RuntimeException ignored) {
                // The result is not a JSON string literal; pass it through unchanged.
            }
        }
        return text;
    }

    static String normalizeJson(String json) {
        try {
            return Json.toJson(Json.fromJson(json, Object.class));
        } catch (RuntimeException ignored) {
            return json;
        }
    }

    /**
     * The engine requires a non-blank id so a result can be matched back to a call; LangChain4j
     * allows none. A generated one is better than a null the caller has to handle.
     */
    static String blankToGenerated(String id) {
        return id == null || id.isBlank() ? generateCallId() : id;
    }

    static String generateCallId() {
        return "call_" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
    }
}
