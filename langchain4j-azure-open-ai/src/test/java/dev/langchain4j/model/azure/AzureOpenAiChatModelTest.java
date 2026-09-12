package dev.langchain4j.model.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.CompletionsFinishReason;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AzureOpenAiChatModelTest {

    @Test
    void should_not_honor_image_detail_level_by_default() {
        // given
        OpenAIClient client = mockClient();
        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .openAIClient(client)
                .deploymentName("test-deployment")
                .build();

        // when
        model.chat(UserMessage.from(
                "Describe this image",
                ImageContent.from("https://example.com/image.png", ImageContent.DetailLevel.HIGH)));

        // then
        String contentJson = capturedUserMessageContent(client).toString();
        assertThat(contentJson).contains("https://example.com/image.png").doesNotContain("\"detail\"");
    }

    @Test
    void should_honor_image_detail_level_when_enabled() {
        // given
        OpenAIClient client = mockClient();
        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .openAIClient(client)
                .deploymentName("test-deployment")
                .honorImageDetailLevel(true)
                .build();

        // when
        model.chat(UserMessage.from(
                "Describe this image",
                ImageContent.from("https://example.com/image.png", ImageContent.DetailLevel.HIGH)));

        // then
        String contentJson = capturedUserMessageContent(client).toString();
        assertThat(contentJson).contains("https://example.com/image.png").contains("\"detail\":\"high\"");
    }

    private static OpenAIClient mockClient() {
        OpenAIClient client = mock(OpenAIClient.class);

        ChatResponseMessage responseMessage = mock(ChatResponseMessage.class);
        when(responseMessage.getContent()).thenReturn("ok");

        ChatChoice choice = mock(ChatChoice.class);
        when(choice.getMessage()).thenReturn(responseMessage);
        when(choice.getFinishReason()).thenReturn(CompletionsFinishReason.STOPPED);

        ChatCompletions chatCompletions = mock(ChatCompletions.class);
        when(chatCompletions.getChoices()).thenReturn(List.of(choice));
        when(chatCompletions.getModel()).thenReturn("test-deployment");

        when(client.getChatCompletions(eq("test-deployment"), any(ChatCompletionsOptions.class)))
                .thenReturn(chatCompletions);

        return client;
    }

    private static Object capturedUserMessageContent(OpenAIClient client) {
        ArgumentCaptor<ChatCompletionsOptions> optionsCaptor = ArgumentCaptor.forClass(ChatCompletionsOptions.class);
        verify(client).getChatCompletions(eq("test-deployment"), optionsCaptor.capture());
        ChatRequestUserMessage userMessage =
                (ChatRequestUserMessage) optionsCaptor.getValue().getMessages().get(0);
        return userMessage.getContent();
    }
}
