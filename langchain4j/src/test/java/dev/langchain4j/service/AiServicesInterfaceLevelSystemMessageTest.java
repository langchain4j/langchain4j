package dev.langchain4j.service;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServicesInterfaceLevelSystemMessageTest {

    static final String SYSTEM_MESSAGE = "Given a name of a country, answer with a name of it's capital";

    @Spy
    ChatModel model = ChatModelMock.thatAlwaysResponds("Berlin");

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(model);
    }

    @SystemMessage(SYSTEM_MESSAGE)
    interface AiService {

        String chat(String userMessage);
    }

    interface BaseAiService {

        String chat(String userMessage);
    }

    @SystemMessage(SYSTEM_MESSAGE)
    interface AiServiceWithInheritedMethod extends BaseAiService {}

    @SystemMessage("This message should be ignored")
    interface AiServiceWithMethodAnnotation {

        @SystemMessage(SYSTEM_MESSAGE)
        String chat(String userMessage);
    }

    @SystemMessage("This message should be ignored")
    interface AnnotatedParentAiService {

        String chat(String userMessage);
    }

    interface AiServiceExtendingAnnotatedParent extends AnnotatedParentAiService {}

    interface BaseAiServiceWithMethodAnnotation {

        @SystemMessage(SYSTEM_MESSAGE)
        String chat(String userMessage);
    }

    @SystemMessage("This message should be ignored")
    interface AiServiceWithInheritedAnnotatedMethod extends BaseAiServiceWithMethodAnnotation {}

    @SystemMessage("Given a name of a country, answer with {{answerInstructions}}")
    interface AiServiceWithTemplate {

        String chat(@UserMessage String userMessage, @V("answerInstructions") String answerInstructions);
    }

    @SystemMessage(fromResource = "chefs-prompt-system-message.txt")
    interface AiServiceWithResource {

        String chat(@UserMessage String userMessage, @V("character") String character);
    }

    @SystemMessage(SYSTEM_MESSAGE)
    interface StreamingAiService {

        TokenStream chat(String userMessage);
    }

    @SystemMessage
    interface AiServiceWithEmptySystemMessage {

        String chat(String userMessage);
    }

    private void verifySystemMessage(String expectedSystemMessage) {
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(systemMessage(expectedSystemMessage), userMessage("Country: Germany"))
                        .build());
    }

    @Test
    void interface_level_system_message() {
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(model).build();

        assertThat(aiService.chat("Country: Germany")).containsIgnoringCase("Berlin");
        verifySystemMessage(SYSTEM_MESSAGE);
    }

    @Test
    void interface_level_system_message_on_ai_service_with_inherited_method() {
        AiServiceWithInheritedMethod aiService = AiServices.builder(AiServiceWithInheritedMethod.class)
                .chatModel(model)
                .build();

        assertThat(aiService.chat("Country: Germany")).containsIgnoringCase("Berlin");
        verifySystemMessage(SYSTEM_MESSAGE);
    }

    @Test
    void method_level_system_message_takes_precedence_over_interface_level() {
        AiServiceWithMethodAnnotation aiService = AiServices.builder(AiServiceWithMethodAnnotation.class)
                .chatModel(model)
                .build();

        assertThat(aiService.chat("Country: Germany")).containsIgnoringCase("Berlin");
        verifySystemMessage(SYSTEM_MESSAGE);
    }

    @Test
    void interface_level_system_message_is_not_inherited_from_parent_interface() {
        AiServiceExtendingAnnotatedParent aiService = AiServices.builder(AiServiceExtendingAnnotatedParent.class)
                .chatModel(model)
                .build();

        assertThat(aiService.chat("Country: Germany")).containsIgnoringCase("Berlin");
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(userMessage("Country: Germany"))
                        .build());
    }

    @Test
    void method_level_system_message_takes_precedence_over_interface_level_for_inherited_method() {
        AiServiceWithInheritedAnnotatedMethod aiService = AiServices.builder(
                        AiServiceWithInheritedAnnotatedMethod.class)
                .chatModel(model)
                .build();

        assertThat(aiService.chat("Country: Germany")).containsIgnoringCase("Berlin");
        verifySystemMessage(SYSTEM_MESSAGE);
    }

    @Test
    void interface_level_system_message_takes_precedence_over_system_message_provider() {
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(model)
                .systemMessageProvider(chatMemoryId -> "This message should be ignored")
                .build();

        assertThat(aiService.chat("Country: Germany")).containsIgnoringCase("Berlin");
        verifySystemMessage(SYSTEM_MESSAGE);
    }

    @Test
    void interface_level_system_message_takes_precedence_over_system_message_provider_with_context() {
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(model)
                .systemMessageProviderWithContext(invocationContext -> "This message should be ignored")
                .build();

        assertThat(aiService.chat("Country: Germany")).containsIgnoringCase("Berlin");
        verifySystemMessage(SYSTEM_MESSAGE);
    }

    @Test
    void interface_level_system_message_with_template_variable() {
        AiServiceWithTemplate aiService =
                AiServices.builder(AiServiceWithTemplate.class).chatModel(model).build();

        assertThat(aiService.chat("Country: Germany", "a name of it's capital")).containsIgnoringCase("Berlin");
        verifySystemMessage(SYSTEM_MESSAGE);
    }

    @Test
    void interface_level_system_message_from_resource() {
        AiServiceWithResource aiService =
                AiServices.builder(AiServiceWithResource.class).chatModel(model).build();

        assertThat(aiService.chat("Country: Germany", "talented")).containsIgnoringCase("Berlin");
        verifySystemMessage("You are very talented chef");
    }

    @Test
    void interface_level_system_message_with_streaming_chat_model() throws Exception {
        StreamingChatModelMock delegate = StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("Berlin"));
        List<ChatRequest> capturedRequests = new ArrayList<>();
        StreamingChatModel streamingModel = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                capturedRequests.add(chatRequest);
                delegate.doChat(chatRequest, handler);
            }
        };

        StreamingAiService aiService = AiServices.builder(StreamingAiService.class)
                .streamingChatModel(streamingModel)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();
        aiService
                .chat("Country: Germany")
                .onPartialResponse(partialResponse -> {})
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();
        futureResponse.get(10, TimeUnit.SECONDS);

        assertThat(capturedRequests).hasSize(1);
        assertThat(capturedRequests.get(0).messages())
                .containsExactly(systemMessage(SYSTEM_MESSAGE), userMessage("Country: Germany"));
    }

    @Test
    void empty_interface_level_system_message_fails() {
        AiServiceWithEmptySystemMessage aiService = AiServices.builder(AiServiceWithEmptySystemMessage.class)
                .chatModel(model)
                .build();

        assertThatThrownBy(() -> aiService.chat("Country: Germany"))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessage("@SystemMessage's template cannot be empty");
    }
}
