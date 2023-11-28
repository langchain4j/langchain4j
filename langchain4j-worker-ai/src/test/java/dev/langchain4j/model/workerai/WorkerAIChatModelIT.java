package dev.langchain4j.model.workerai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("requires a worker ai account")
@EnabledIfEnvironmentVariable(named = "WORKERAI_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "WORKERAI_ACCOUNT_ID", matches = ".*")
public class WorkerAIChatModelIT {

    static WorkerAiChatModel chatModel;

    @BeforeAll
    public static void initializeModel() {
        chatModel = WorkerAiChatModel.builder()
                .modelName(WorkerAiModelName.LLAMA2_7B_FULL)
                .accountIdentifier(System.getenv("WORKERAI_ACCOUNT_ID"))
                .token(System.getenv("WORKERAI_API_KEY"))
                .buildChatModel();
    }

    @Test
    void should_generate_answer_and_return_finish_reason_stop() {
        UserMessage userMessage = userMessage("hello, how are you?");
        Response<AiMessage> response = chatModel.generate(userMessage);
        assertThat(response.content().text()).isNotBlank();
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
        Response<AiMessage> response = chatModel.generate(conversation);
        Assertions.assertNotNull(response);
        assertThat(response.content().text()).isNotBlank();
        Assertions.assertEquals("PARIS", chatModel.generate(conversation).content().text().toUpperCase());
    }

    @Test
    public void should_throw_unsupported_if_using_toolSpecification() {
        List<ToolSpecification> toolSpecifications = new ArrayList<>();
        toolSpecifications.add(ToolSpecification.builder().build());
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(userMessage("hello, how are you?"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            chatModel.generate(messages, toolSpecifications);
        });
    }




}
