package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiServiceWIthStructuredResponseTest {

    public enum RequestCategory {
        LEGAL, MEDICAL, TECHNICAL, UNKNOWN
    }

    public record RequestClassifierResponse(RequestCategory category) {
    }

    public interface CategoryRouter {
        RequestClassifierResponse classify(@UserMessage String request);
    }

    static class LowerCaseEnumChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from("{\"category\" : \"legal\"}")).build();
        }
    }

    @Test
    void caseInsesitiveEnum() {
        CategoryRouter categoryRouter = AiServices.builder(CategoryRouter.class)
                .chatModel(new LowerCaseEnumChatModel())
                .build();

        RequestClassifierResponse response = categoryRouter.classify("Some request");
        assertThat(response.category()).isEqualTo(RequestCategory.LEGAL);
    }
}
