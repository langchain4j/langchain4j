package dev.langchain4j.model.anthropic;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicMessages;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicSystemPrompt;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicToolChoice;
import static dev.langchain4j.model.anthropic.internal.mapper.AnthropicMapper.toAnthropicTools;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.chat.request.ResponseFormatType.TEXT;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCacheType;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContainer;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContainer.AnthropicContainerSkill;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicFormat;
import dev.langchain4j.model.anthropic.internal.api.AnthropicMetadata;
import dev.langchain4j.model.anthropic.internal.api.AnthropicOutputConfig;
import dev.langchain4j.model.anthropic.internal.api.AnthropicThinking;
import dev.langchain4j.model.anthropic.internal.api.AnthropicTool;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Internal
class InternalAnthropicHelper {

    /**
     * The {@code skill_id} marker used for Anthropic-managed skills in the {@code container.skills} block.
     */
    private static final String ANTHROPIC_SKILL_TYPE = "anthropic";

    /**
     * Version sent for each Anthropic-managed skill. {@code latest} always resolves to the newest skill version.
     */
    private static final String SKILL_VERSION_LATEST = "latest";

    /**
     * Server tool entry that must accompany skills so Claude can run them in the code execution container.
     */
    private static final String CODE_EXECUTION_TOOL_TYPE = "code_execution_20250825";

    private static final String CODE_EXECUTION_TOOL_NAME = "code_execution";

    /**
     * {@code anthropic-beta} tokens required to use Skills and download the files they produce.
     */
    private static final List<String> SKILLS_BETA_FEATURES =
            List.of("code-execution-2025-08-25", "skills-2025-10-02", "files-api-2025-04-14");

    private InternalAnthropicHelper() {}

    /**
     * Returns the {@code anthropic-beta} header value augmented with the tokens required by {@link AnthropicSkill},
     * preserving any user-supplied beta features and avoiding duplicates. Returns {@code beta} unchanged when no
     * skills are configured.
     */
    static String addSkillsBeta(String beta, List<AnthropicSkill> skills) {
        if (isNullOrEmpty(skills)) {
            return beta;
        }
        Set<String> features = new LinkedHashSet<>();
        if (beta != null && !beta.isBlank()) {
            for (String feature : beta.split(",")) {
                if (!feature.isBlank()) {
                    features.add(feature.trim());
                }
            }
        }
        features.addAll(SKILLS_BETA_FEATURES);
        return String.join(",", features);
    }

    private static AnthropicContainer toAnthropicContainer(List<AnthropicSkill> skills) {
        // Drop nulls and duplicates: the API rejects duplicate skill entries, and a null would NPE below.
        List<AnthropicContainerSkill> containerSkills = skills.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(skill -> new AnthropicContainerSkill(ANTHROPIC_SKILL_TYPE, skill.skillId(), SKILL_VERSION_LATEST))
                .toList();
        return new AnthropicContainer(containerSkills);
    }

    private static AnthropicTool codeExecutionTool() {
        Map<String, Object> customParameters = new LinkedHashMap<>();
        customParameters.put("type", CODE_EXECUTION_TOOL_TYPE);
        return AnthropicTool.builder()
                .name(CODE_EXECUTION_TOOL_NAME)
                .customParameters(customParameters)
                .build();
    }

    /**
     * Detects an already-configured code execution server tool by its {@code type}
     * (e.g. {@code code_execution_20250825}) rather than by name, so a regular tool that happens to be named
     * {@code code_execution} does not suppress the server tool that Skills require.
     */
    private static boolean hasCodeExecutionTool(List<AnthropicTool> tools) {
        return tools.stream()
                .anyMatch(tool -> tool.customParameters() != null
                        && tool.customParameters().get("type") instanceof String type
                        && type.startsWith(CODE_EXECUTION_TOOL_NAME));
    }

    static void validate(ChatRequestParameters parameters) {
        List<String> unsupportedFeatures = new ArrayList<>();
        if (parameters.frequencyPenalty() != null) {
            unsupportedFeatures.add("Frequency Penalty");
        }
        if (parameters.presencePenalty() != null) {
            unsupportedFeatures.add("Presence Penalty");
        }
        if (parameters.responseFormat() != null
                && parameters.responseFormat().type() == JSON
                && parameters.responseFormat().jsonSchema() == null) {
            unsupportedFeatures.add("Schemaless JSON response format");
        }

        if (!unsupportedFeatures.isEmpty()) {
            if (unsupportedFeatures.size() == 1) {
                throw new UnsupportedFeatureException(unsupportedFeatures.get(0) + " is not supported by Anthropic");
            }
            throw new UnsupportedFeatureException(
                    String.join(", ", unsupportedFeatures) + " are not supported by Anthropic");
        }
    }

    static AnthropicCreateMessageRequest createAnthropicRequest(
            ChatRequest chatRequest,
            AnthropicThinking thinking,
            boolean sendThinking,
            boolean midConversationSystemMessages,
            AnthropicCacheType cacheType,
            AnthropicCacheType toolsCacheType,
            boolean stream,
            String toolChoiceName,
            Boolean disableParallelToolUse,
            List<AnthropicServerTool> serverTools,
            Set<String> toolMetadataKeysToSend,
            String userId,
            List<AnthropicSkill> skills,
            Map<String, Object> customParameters,
            Boolean strictTools) {

        AnthropicCreateMessageRequest.Builder requestBuilder = AnthropicCreateMessageRequest.builder().stream(stream)
                .model(chatRequest.modelName())
                .messages(toAnthropicMessages(chatRequest.messages(), sendThinking, midConversationSystemMessages))
                .system(toAnthropicSystemPrompt(chatRequest.messages(), cacheType, midConversationSystemMessages))
                .maxTokens(chatRequest.maxOutputTokens())
                .stopSequences(chatRequest.stopSequences())
                .temperature(chatRequest.temperature())
                .topP(chatRequest.topP())
                .topK(chatRequest.topK())
                .thinking(thinking)
                .outputConfig(toAnthropicOutputConfig(chatRequest.responseFormat()))
                .customParameters(customParameters);

        List<AnthropicTool> tools = new ArrayList<>();
        if (!isNullOrEmpty(serverTools)) {
            tools.addAll(toAnthropicTools(serverTools));
        }
        if (!isNullOrEmpty(chatRequest.toolSpecifications())) {
            tools.addAll(toAnthropicTools(
                    chatRequest.toolSpecifications(), toolsCacheType, toolMetadataKeysToSend, strictTools));
        }
        if (!isNullOrEmpty(skills)) {
            AnthropicContainer container = toAnthropicContainer(skills);
            if (!isNullOrEmpty(container.skills)) {
                requestBuilder.container(container);
                if (!hasCodeExecutionTool(tools)) {
                    tools.add(codeExecutionTool());
                }
            }
        }
        if (!tools.isEmpty()) {
            requestBuilder.tools(tools);
        }

        if (chatRequest.toolChoice() != null) {
            requestBuilder.toolChoice(
                    toAnthropicToolChoice(chatRequest.toolChoice(), toolChoiceName, disableParallelToolUse));
        }

        if (!isNullOrEmpty(userId)) {
            requestBuilder.metadata(AnthropicMetadata.builder().userId(userId).build());
        }

        return requestBuilder.build();
    }

    public static AnthropicOutputConfig toAnthropicOutputConfig(ResponseFormat responseFormat) {
        if (responseFormat == null || responseFormat.type() == TEXT || responseFormat.jsonSchema() == null) {
            return null;
        }

        return AnthropicOutputConfig.builder()
                .format(AnthropicFormat.fromJsonSchema(responseFormat.jsonSchema()))
                .build();
    }
}
