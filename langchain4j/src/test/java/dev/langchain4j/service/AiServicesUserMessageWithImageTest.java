package dev.langchain4j.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Test to reproduce the issue where UserMessage with ImageContent loses image information
 * when passed through AiServices with StreamingChatModel.
 * <p>
 * Issue: When a UserMessage containing ImageContent is passed as a parameter to an AI service method,
 * the image content is lost and converted to a plain text string representation.
 */
class AiServicesUserMessageWithImageTest {

    interface AiService {
        TokenStream chat(UserMessage message);
    }

    @Test
    void should_preserve_image_content_when_user_message_passed_as_parameter() throws Exception {
        // given - Create a UserMessage with both text and image content
        String imageUrl = "https://example.com/image.jpg";
        UserMessage userMessage =
                UserMessage.from(ImageContent.from(imageUrl), TextContent.from("What do you see in this image?"));

        // Create a mock streaming model that returns a simple response
        StreamingChatModel mockModel =
                spy(StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("I see a cat in the image.")));

        // Build the AI service
        AiService aiService = AiServices.builder(AiService.class)
                .streamingChatModel(mockModel)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when - Call the service with UserMessage containing image
        aiService
                .chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();

        ChatResponse response = futureResponse.get(5, SECONDS);

        // then - Verify the response
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).isEqualTo("I see a cat in the image.");

        // Capture the actual ChatRequest sent to the model
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(mockModel).chat(requestCaptor.capture(), any());

        ChatRequest actualRequest = requestCaptor.getValue();
        List<ChatMessage> messages = actualRequest.messages();

        // Verify that the message was sent
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);

        UserMessage actualUserMessage = (UserMessage) messages.get(0);

        // THIS IS THE BUG: The UserMessage should preserve the original ImageContent
        // Currently, it fails because the UserMessage is converted to a string representation
        // and then recreated as a new UserMessage with only text content
        assertThat(actualUserMessage.contents())
                .as("UserMessage should contain 2 contents: ImageContent and TextContent")
                .hasSize(2);

        assertThat(actualUserMessage.contents().get(0))
                .as("First content should be ImageContent")
                .isInstanceOf(ImageContent.class);

        ImageContent actualImageContent =
                (ImageContent) actualUserMessage.contents().get(0);
        assertThat(actualImageContent.image().url().toString())
                .as("Image URL should be preserved")
                .isEqualTo(imageUrl);

        assertThat(actualUserMessage.contents().get(1))
                .as("Second content should be TextContent")
                .isInstanceOf(TextContent.class);

        TextContent actualTextContent =
                (TextContent) actualUserMessage.contents().get(1);
        assertThat(actualTextContent.text())
                .as("Text content should be preserved")
                .isEqualTo("What do you see in this image?");

        // Success output
        System.out.println("✅ Test passed: URL image content preserved successfully!");
        System.out.println("   - Image URL: " + actualImageContent.image().url());
        System.out.println("   - Text content: " + actualTextContent.text());
        System.out.println(
                "   - Total contents: " + actualUserMessage.contents().size());
    }

    @Test
    void should_preserve_base64_image_content_when_user_message_passed_as_parameter() throws Exception {
        // given - Create a UserMessage with base64 encoded image
        String base64Data =
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String mimeType = "image/png";

        UserMessage userMessage =
                UserMessage.from(ImageContent.from(base64Data, mimeType), TextContent.from("Describe this image"));

        // Create a mock streaming model
        StreamingChatModel mockModel =
                spy(StreamingChatModelMock.thatAlwaysStreams(AiMessage.from("This is a 1x1 pixel red image.")));

        // Build the AI service
        AiService aiService = AiServices.builder(AiService.class)
                .streamingChatModel(mockModel)
                .build();

        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        aiService
                .chat(userMessage)
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(futureResponse::complete)
                .onError(futureResponse::completeExceptionally)
                .start();

        ChatResponse response = futureResponse.get(5, SECONDS);

        // then
        assertThat(response).isNotNull();

        // Capture the actual ChatRequest
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(mockModel).chat(requestCaptor.capture(), any());

        ChatRequest actualRequest = requestCaptor.getValue();
        List<ChatMessage> messages = actualRequest.messages();

        assertThat(messages).hasSize(1);
        UserMessage actualUserMessage = (UserMessage) messages.get(0);

        // Verify image content is preserved
        assertThat(actualUserMessage.contents()).hasSize(2);
        assertThat(actualUserMessage.contents().get(0)).isInstanceOf(ImageContent.class);

        ImageContent actualImageContent =
                (ImageContent) actualUserMessage.contents().get(0);
        assertThat(actualImageContent.image().base64Data())
                .as("Base64 image data should be preserved")
                .isEqualTo(base64Data);
        assertThat(actualImageContent.image().mimeType())
                .as("Image mime type should be preserved")
                .isEqualTo(mimeType);

        // Success output
        System.out.println("✅ Test passed: Base64 image content preserved successfully!");
        System.out.println("   - Image mime type: " + actualImageContent.image().mimeType());
        System.out.println("   - Base64 data length: "
                + actualImageContent.image().base64Data().length() + " characters");
        System.out.println("   - Text content: "
                + ((TextContent) actualUserMessage.contents().get(1)).text());
        System.out.println(
                "   - Total contents: " + actualUserMessage.contents().size());
    }
}
