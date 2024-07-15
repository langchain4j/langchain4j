package dev.langchain4j.model.ollama.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

class AiStatsMessage extends AiMessage {

    final TokenUsage tokenUsage;

    AiStatsMessage(String text, TokenUsage tokenUsage) {
        super(text);
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokeUsage");
    }

    AiStatsMessage(List<ToolExecutionRequest> toolExecutionRequests, TokenUsage tokenUsage) {
        super(toolExecutionRequests);
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokeUsage");
    }

    AiStatsMessage(String text, List<ToolExecutionRequest> toolExecutionRequests, TokenUsage tokenUsage) {
        super(text, toolExecutionRequests);
        this.tokenUsage = ValidationUtils.ensureNotNull(tokenUsage, "tokenUsage");
    }

    TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    static AiStatsMessage from(AiMessage aiMessage, TokenUsage tokenUsage) {
        if (aiMessage.text() == null) {
            return new AiStatsMessage(aiMessage.toolExecutionRequests(), tokenUsage);
        } else if (aiMessage.hasToolExecutionRequests()) {
            return new AiStatsMessage(aiMessage.text(), aiMessage.toolExecutionRequests(), tokenUsage);
        } else {
            return new AiStatsMessage(aiMessage.text(), tokenUsage);
        }
    }
}
