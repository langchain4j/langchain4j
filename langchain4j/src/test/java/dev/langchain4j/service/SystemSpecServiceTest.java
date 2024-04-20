package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.SystemSpec;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.service.SystemSpecService.fetchSystemSpecUsingAI;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SystemSpecServiceTest {

    @Test
    public void should_dynamically_select_the_system_and_respond_user_message_with_ai() {
        // Given
        ChatLanguageModel mockChatLanguageModel = mock(ChatLanguageModel.class);
        List<SystemSpec> systemSpecs = new ArrayList<>();

        SystemSpec systemSpec1 = SystemSpec.builder()
                .name("SYSTEM_1")
                .description("Test System 1")
                .template(new String[]{"System 1 template: %s"})
                .build();
        SystemSpec systemSpec2 = SystemSpec.builder()
                .name("SYSTEM_2")
                .description("Test System 2")
                .template(new String[]{"System 2 template: %s"})
                .build();

        systemSpecs.add(systemSpec1);
        systemSpecs.add(systemSpec2);

        Response<AiMessage> aiMessageResponse = mock(Response.class);
        AiMessage aiMessage = mock(AiMessage.class);

        when(mockChatLanguageModel.generate(any(UserMessage.class))).thenReturn(aiMessageResponse);
        when(aiMessageResponse.content()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn("SYSTEM_1");


        // When
        String userMessage = "This is a test message for System 1.";
        SystemSpec systemSpec = fetchSystemSpecUsingAI(userMessage, systemSpecs, mockChatLanguageModel);

        // Then
        assertNotNull(systemSpec);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    public void should_fail_when_user_message_is_null_or_blank(String userMessage) {
        // Given
        ChatLanguageModel mockChatLanguageModel = mock(ChatLanguageModel.class);
        List<SystemSpec> systemSpecs = new ArrayList<>();
        SystemSpec systemSpec1 = SystemSpec.builder()
                .name("SYSTEM_1")
                .description("Test System 1")
                .template(new String[]{"System 1 template: %s"})
                .build();
        systemSpecs.add(systemSpec1);

        // when-then
        assertThatThrownBy(() -> fetchSystemSpecUsingAI(userMessage, systemSpecs, mockChatLanguageModel))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("userMessage cannot be null or blank");
    }

    @Test
    public void should_fallback_to_default_system_when_exception() {
        // Given
        ChatLanguageModel mockChatLanguageModel = mock(ChatLanguageModel.class);
        List<SystemSpec> systemSpecs = new ArrayList<>();

        SystemSpec systemSpec1 = SystemSpec.builder()
                .name("SYSTEM_1")
                .description("Test System 1")
                .template(new String[]{"System 1 template: %s"})
                .build();
        SystemSpec systemSpec2 = SystemSpec.builder()
                .name("SYSTEM_2")
                .description("Test System 2")
                .template(new String[]{"System 2 template: %s"})
                .build();

        systemSpecs.add(systemSpec1);
        systemSpecs.add(systemSpec2);

        Response<AiMessage> aiMessageResponse = mock(Response.class);
        AiMessage aiMessage = mock(AiMessage.class);

        when(mockChatLanguageModel.generate(anyList()))
                .thenThrow(IllegalArgumentException.class)
                .thenReturn(aiMessageResponse);
        when(aiMessageResponse.content()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn("SYSTEM_1");

        // When
        String userMessage = "This is a test message for System 1.";
        SystemSpec systemSpec = fetchSystemSpecUsingAI(userMessage, systemSpecs, mockChatLanguageModel);

        // Then
        assertNotNull(systemSpec);

    }
}