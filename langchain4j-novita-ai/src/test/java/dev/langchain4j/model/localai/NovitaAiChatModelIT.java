package dev.langchain4j.model.localai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.novitaai.NovitaAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.novitaai.NovitaAiChatModelName.DEEPSEEK_V3;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

@EnabledIfEnvironmentVariable(named = "NOVITA_AI_API_KEY", matches = ".*")
@Slf4j
class NovitaAiChatModelIT {

    static NovitaAiChatModel chatModel;

    @BeforeAll
    static void initializeModel() {
        chatModel = NovitaAiChatModel.builder()
                .modelName(DEEPSEEK_V3)
                .apiKey(System.getenv("NOVITA_AI_API_KEY"))
                .build();
    }

    @Test
    void should_generate_answer_and_return_finish_reason_stop() {
        UserMessage userMessage = userMessage("hello, how are you?");
        ChatResponse response = chatModel.chat(userMessage);
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_based_on_context() {
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(systemMessage("You an an assistant i will give you the name of " +
                "a country and you will give me exactly the name of the capital, " +
                "no other text or message, " +
                "just the name of the city"));
        conversation.add(userMessage("France"));
        ChatResponse response = chatModel.chat(conversation);
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();
        assertThat(chatModel.chat(conversation).aiMessage().text().toUpperCase()).isEqualTo("PARIS");
    }

    @Test
    void should_throw_unsupported_if_using_toolSpecification() {
        List<ToolSpecification> toolSpecifications = new ArrayList<>();
        toolSpecifications.add(ToolSpecification.builder().build());
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(userMessage("hello, how are you?"));

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();

        assertThatExceptionOfType(UnsupportedFeatureException.class).isThrownBy(() -> {
            chatModel.chat(request);
        });
    }
}
