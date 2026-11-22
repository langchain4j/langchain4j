package com.example.support;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.test.mock.MockChatModel;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The models of the application, replaced by the mocks of langchain4j-test.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestModels {

    public static final String STREAMED_ANSWER = "Hello from Miles of Smiles";
    public static final TokenUsage TOKEN_USAGE_PER_CALL = new TokenUsage(20, 10);

    @Bean
    ChatModel chatModel() {
        return MockChatModel.thatResponds(SupportScript::respond).thenRepeats().withTokenUsage(TOKEN_USAGE_PER_CALL);
    }

    @Bean
    StreamingChatModel streamingChatModel() {
        return MockStreamingChatModel.thatResponds(STREAMED_ANSWER).thenRepeats().withTokenUsage(TOKEN_USAGE_PER_CALL);
    }
}
