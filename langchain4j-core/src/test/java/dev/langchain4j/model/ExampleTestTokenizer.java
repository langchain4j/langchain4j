package dev.langchain4j.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;

@SuppressWarnings("deprecation")
public class ExampleTestTokenizer implements Tokenizer {
    @Override
    public int estimateTokenCountInText(String text) {
        return text.split(" ").length;
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInText(message.text());
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int tokenCount = 0;
        for (ChatMessage message : messages) {
            tokenCount += estimateTokenCountInMessage(message);
        }
        return tokenCount;
    }

    @Override
    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        int tokenCount = 0;
        for (ToolSpecification specification : toolSpecifications) {
            tokenCount += estimateTokenCountInText(specification.description());
        }
        return tokenCount;
    }

    @Override
    public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
        int tokenCount = 0;
        for (ToolExecutionRequest request : toolExecutionRequests) {
            tokenCount += estimateTokenCountInText(request.arguments());
        }
        return tokenCount;
    }
}
