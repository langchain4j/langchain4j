package dev.langchain4j.model.azure;

import static dev.langchain4j.model.chat.request.DefaultChatRequestParameters.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.ai.openai.models.CompletionsFinishReason;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AzureOpenAiChatModelEmptyChoicesTest {

    @Test
    void should_throw_IllegalArgumentException_when_azure_response_has_empty_choices() {
        // given
        OpenAIClient mockClient = mock(OpenAIClient.class);

        // Return a ChatCompletions with empty choices
        ChatCompletions emptyChoicesResponse = mock(ChatCompletions.class);
        when(emptyChoicesResponse.getChoices()).thenReturn(Collections.emptyList());

        when(mockClient.getChatCompletions(any(String.class), any(ChatCompletionsOptions.class)))
                .thenReturn(emptyChoicesResponse);

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .openAIClient(mockClient)
                .deploymentName("gpt-4o")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(dev.langchain4j.data.message.UserMessage.from("hello")))
                .parameters(EMPTY.withModelName("gpt-4o"))
                .build();

        // when/then
        assertThatThrownBy(() -> model.doChat(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Azure OpenAI response has no choices");
    }

    @Test
    void should_throw_IllegalArgumentException_when_azure_response_has_null_choices() {
        // given
        OpenAIClient mockClient = mock(OpenAIClient.class);

        // Return a ChatCompletions with null choices
        ChatCompletions nullChoicesResponse = mock(ChatCompletions.class);
        when(nullChoicesResponse.getChoices()).thenReturn(null);

        when(mockClient.getChatCompletions(any(String.class), any(ChatCompletionsOptions.class)))
                .thenReturn(nullChoicesResponse);

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .openAIClient(mockClient)
                .deploymentName("gpt-4o")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(dev.langchain4j.data.message.UserMessage.from("hello")))
                .parameters(EMPTY.withModelName("gpt-4o"))
                .build();

        // when/then
        assertThatThrownBy(() -> model.doChat(request))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Azure OpenAI response has no choices");
    }

    @Test
    void should_return_chat_response_when_azure_response_has_choices() {
        // given
        OpenAIClient mockClient = mock(OpenAIClient.class);

        ChatChoice mockChoice = mock(ChatChoice.class);
        ChatResponseMessage mockMessage = mock(ChatResponseMessage.class);
        when(mockMessage.getContent()).thenReturn("Hello, world!");

        when(mockChoice.getMessage()).thenReturn(mockMessage);
        when(mockChoice.getFinishReason()).thenReturn(CompletionsFinishReason.STOPPED);

        ChatCompletions validResponse = mock(ChatCompletions.class);
        when(validResponse.getChoices()).thenReturn(List.of(mockChoice));
        when(validResponse.getId()).thenReturn("chatcmpl-123");
        when(validResponse.getModel()).thenReturn("gpt-4o");

        ChatCompletions.Usage mockUsage = mock(ChatCompletions.Usage.class);
        when(mockUsage.getCompletionTokens()).thenReturn(5);
        when(mockUsage.getPromptTokens()).thenReturn(2);
        when(mockUsage.getTotalTokens()).thenReturn(7);
        when(validResponse.getUsage()).thenReturn(mockUsage);

        when(mockClient.getChatCompletions(any(String.class), any(ChatCompletionsOptions.class)))
                .thenReturn(validResponse);

        AzureOpenAiChatModel model = AzureOpenAiChatModel.builder()
                .openAIClient(mockClient)
                .deploymentName("gpt-4o")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(dev.langchain4j.data.message.UserMessage.from("hello")))
                .parameters(EMPTY.withModelName("gpt-4o"))
                .build();

        // when
        ChatResponse response = model.doChat(request);

        // then
        assertThat(response.aiMessage().text()).isEqualTo("Hello, world!");
    }
}