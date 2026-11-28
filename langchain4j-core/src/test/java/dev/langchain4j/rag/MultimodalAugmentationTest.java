package dev.langchain4j.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MultimodalAugmentationTest {

    private static final String QUESTION = "What is the warranty for this model?";
    private static final String WARRANTY = "This model has a two-year warranty.";

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_augment_text_only_user_message(boolean async) throws Exception {
        AugmentationResult result = augment(UserMessage.from(QUESTION), async);

        assertThat(((UserMessage) result.chatMessage()).singleText()).contains(QUESTION, WARRANTY);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_augment_text_and_keep_images_in_order(boolean async) throws Exception {
        ImageContent firstImage = ImageContent.from("https://example.com/product.jpg");
        ImageContent secondImage = ImageContent.from("https://example.com/label.jpg");
        UserMessage message = UserMessage.from(firstImage, TextContent.from(QUESTION), secondImage);

        AugmentationResult result = augment(message, async);

        assertThat(((UserMessage) result.chatMessage()).contents())
                .satisfiesExactly(
                        part -> assertThat(part).isEqualTo(firstImage),
                        text -> assertThat(text)
                                .isInstanceOfSatisfying(
                                        TextContent.class,
                                        part -> assertThat(part.text()).contains(QUESTION, WARRANTY)),
                        part -> assertThat(part).isEqualTo(secondImage));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_reject_messages_without_exactly_one_text_part(boolean async) {
        ImageContent image = ImageContent.from("https://example.com/product.jpg");
        UserMessage imageOnly = UserMessage.from(image);
        UserMessage multipleTexts = UserMessage.from(TextContent.from(QUESTION), image, TextContent.from("More text"));

        assertThatThrownBy(() -> augment(imageOnly, async)).hasMessageContaining("Expecting single text content");
        assertThatThrownBy(() -> augment(multipleTexts, async)).hasMessageContaining("Expecting single text content");
    }

    private static AugmentationResult augment(UserMessage message, boolean async) throws Exception {
        DefaultRetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(query -> {
                    assertThat(query.text()).isEqualTo(QUESTION);
                    return List.of(Content.from(WARRANTY));
                })
                .offloadBlocking(true)
                .executor(Runnable::run)
                .build();
        AugmentationRequest request = new AugmentationRequest(message, Metadata.from(message, null, List.of()));
        return async ? augmentor.augmentAsync(request).get(5, TimeUnit.SECONDS) : augmentor.augment(request);
    }
}
