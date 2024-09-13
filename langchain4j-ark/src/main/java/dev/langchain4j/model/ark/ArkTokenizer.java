package dev.langchain4j.model.ark;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;

import java.util.Collections;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

public class ArkTokenizer implements Tokenizer {

    private final String apiKey;
    private final String model;

    public ArkTokenizer(String apiKey, String model) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("Ark api key must be defined. It can be generated here: https://www.volcengine.com/docs/82379/1263279");
        }
        this.apiKey = apiKey;
        this.model = model;
        throw new RuntimeException("Ark are currently not support tokenizer!");
    }

    @Override
    public int estimateTokenCountInText(String text) {
        throw new RuntimeException("Ark are currently not support tokenizer!");
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInMessages(Collections.singleton(message));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        throw new RuntimeException("Ark are currently not support tokenizer!");
    }

    @Override
    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported by this tokenizer");
    }

    @Override
    public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
        throw new IllegalArgumentException("Tools are currently not supported by this tokenizer");
    }
}
