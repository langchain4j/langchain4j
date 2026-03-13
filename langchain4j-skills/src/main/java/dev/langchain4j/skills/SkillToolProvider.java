package dev.langchain4j.skills;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.skills.ActivateSkillToolExecutor.ACTIVATED_SKILL_ATTRIBUTE;

class SkillToolProvider implements ToolProvider {

    private final String skillName;
    private final List<ToolProvider> delegateProviders;

    SkillToolProvider(String skillName, List<ToolProvider> delegateProviders) {
        this.skillName = ensureNotBlank(skillName, "skillName");
        this.delegateProviders = copy(ensureNotEmpty(delegateProviders, "delegateProviders"));
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        if (!isSkillActivated(request.messages(), skillName)) {
            return ToolProviderResult.builder().build();
        }

        Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        Set<String> immediateReturnToolNames = new HashSet<>();
        for (ToolProvider delegate : delegateProviders) {
            ToolProviderResult delegateResult = delegate.provideTools(request);
            if (delegateResult != null) {
                // TODO check for unique names
                tools.putAll(delegateResult.tools()); // TODO SearchBehavior.ALWAYS_VISIBLE?
                immediateReturnToolNames.addAll(delegateResult.immediateReturnToolNames());
            }
        }
        return ToolProviderResult.builder()
                .addAll(tools)
                .immediateReturnToolNames(immediateReturnToolNames)
                .build();
    }

    private static boolean isSkillActivated(List<ChatMessage> messages, String skillName) {
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage toolResult) {
                if (skillName.equals(toolResult.attributes().get(ACTIVATED_SKILL_ATTRIBUTE))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}