package dev.langchain4j.model.dashscope;

import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.tokenizers.Tokenization;
import com.alibaba.dashscope.tokenizers.TokenizationResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import lombok.Builder;

import java.util.Collections;

import static dev.langchain4j.internal.Utils.*;
import static dev.langchain4j.model.dashscope.QwenHelper.toQwenMessages;
import static dev.langchain4j.model.dashscope.QwenModelName.QWEN_PLUS;

public class QwenTokenizer implements Tokenizer {
    private final String apiKey;
    private final String modelName;
    private final Tokenization tokenizer;

    @Builder
    public QwenTokenizer(String apiKey, String modelName) {
        if (isNullOrBlank(apiKey)) {
            throw new IllegalArgumentException("DashScope api key must be defined. It can be generated here: https://dashscope.console.aliyun.com/apiKey");
        }
        this.apiKey = apiKey;
        this.modelName = getOrDefault(modelName, QWEN_PLUS);
        this.tokenizer = new Tokenization();
    }

    @Override
    public int estimateTokenCountInText(String text) {
        String prompt = isBlank(text) ? text + "_" : text;
        try {
            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .prompt(prompt)
                    .build();

            TokenizationResult result = tokenizer.call(param);
            int tokenCount = result.getUsage().getInputTokens();
            return prompt == text ? tokenCount : tokenCount - 1;
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        return estimateTokenCountInMessages(Collections.singleton(message));
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        if (isNullOrEmpty(messages)) {
            return 0;
        }

        try {
            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .messages(toQwenMessages(messages))
                    .build();

            TokenizationResult result = tokenizer.call(param);
            return result.getUsage().getInputTokens();
        } catch (NoApiKeyException | InputRequiredException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int estimateTokenCountInToolSpecifications(Iterable<ToolSpecification> toolSpecifications) {
        throw new IllegalArgumentException("Tools are currently not supported by this tokenizer");
    }

    @Override
    public int estimateTokenCountInToolExecutionRequests(Iterable<ToolExecutionRequest> toolExecutionRequests) {
        throw new IllegalArgumentException("Tools are currently not supported by this tokenizer");
    }

    public static boolean isBlank(CharSequence cs) {
        int strLen = cs == null ? 0 : cs.length();
        for (int i = 0; i < strLen; ++i) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
