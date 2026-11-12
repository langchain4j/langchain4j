package com.example.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Collections;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The models of the application, replaced by the shared mocks of langchain4j-core.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestModels {

    public static final String STREAMED_ANSWER = "Hello from Miles of Smiles";
    public static final TokenUsage TOKEN_USAGE_PER_CALL = new TokenUsage(20, 10);

    @Bean
    ChatModel chatModel() {
        return ChatModelMock.thatResponds(SupportScript::respond)
                .withTokenUsage(TOKEN_USAGE_PER_CALL)
                .withFinishReason(FinishReason.STOP);
    }

    @Bean
    StreamingChatModel streamingChatModel() {
        // thatAlwaysStreams(...) answers only once: every response is consumed from a queue
        return StreamingChatModelMock.thatAlwaysStreams(
                        Collections.nCopies(1_000, AiMessage.from(STREAMED_ANSWER)))
                .withTokenUsage(TOKEN_USAGE_PER_CALL)
                .withFinishReason(FinishReason.STOP);
    }
}
