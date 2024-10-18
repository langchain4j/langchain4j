package dev.langchain4j.model.chatglm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CHATGLM_BASE_URL", matches = ".+")
class ChatGlmChatModelIT {

    ChatLanguageModel model = ChatGlmChatModel.builder()
            .baseUrl(System.getenv("CHATGLM_BASE_URL"))
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_generate_answer() {
        UserMessage userMessage = userMessage("你好，请问一下德国的首都是哪里呢？");
        Response<AiMessage> response = model.generate(userMessage);
        assertThat(response.content().text()).contains("柏林");
    }

    @Test
    void should_generate_answer_from_history() {
        // init history
        List<ChatMessage> messages = new ArrayList<>();

        // given question first time
        UserMessage userMessage = userMessage("你好，请问一下德国的首都是哪里呢？");
        Response<AiMessage> response = model.generate(userMessage);
        assertThat(response.content().text()).contains("柏林");

        // given question with history
        messages.add(userMessage);
        messages.add(response.content());

        UserMessage secondUserMessage = userMessage("你能告诉我上个问题我问了你什么呢？请把我的问题原封不动的告诉我");
        messages.add(secondUserMessage);

        Response<AiMessage> secondResponse = model.generate(messages);
        assertThat(secondResponse.content().text()).contains("德国"); // the answer should contain Germany in the First Question
    }
}
