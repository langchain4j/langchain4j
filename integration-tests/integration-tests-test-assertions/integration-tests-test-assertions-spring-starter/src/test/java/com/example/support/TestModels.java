package com.example.support;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.test.mock.ChatModelMock;
import dev.langchain4j.test.mock.StreamingChatModelMock;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The models that the {@code @AiService} interfaces name, replaced by the mocks of langchain4j-test.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestModels {

    static final String STREAMED_ANSWER = "Hello from Miles of Smiles";
    static final TokenUsage TOKEN_USAGE_PER_CALL = new TokenUsage(20, 10);

    @Bean
    ChatModel chatModel() {
        return ChatModelMock.thatResponds(SupportScript::respond)
                .withTokenUsage(TOKEN_USAGE_PER_CALL)
                .withFinishReason(FinishReason.STOP);
    }

    @Bean
    StreamingChatModel streamingChatModel() {
        return StreamingChatModelMock.thatStreams("Hello ", "from ", "Miles of Smiles")
                .withTokenUsage(TOKEN_USAGE_PER_CALL)
                .withFinishReason(FinishReason.STOP);
    }
}
