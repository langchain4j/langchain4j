package dev.langchain4j.model.watsonx.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.ibm.watsonx.ai.chat.ChatModeration;
import com.ibm.watsonx.ai.chat.TextChatResponse.ModerationResult;
import com.ibm.watsonx.ai.chat.exception.ModerationException;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.watsonx.WatsonxChatModel;
import dev.langchain4j.model.watsonx.WatsonxChatRequestParameters;
import dev.langchain4j.model.watsonx.WatsonxChatResponseMetadata;
import dev.langchain4j.model.watsonx.WatsonxStreamingChatModel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
public class WatsonxChatModelModerationIT {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String PROJECT_ID = System.getenv("WATSONX_PROJECT_ID");
    static final String URL = System.getenv("WATSONX_URL");
    static final String MODEL = "ibm/granite-4-h-small";

    static final SystemMessage SYSTEM_MESSAGE =
            SystemMessage.from("You are a helpful assistant. You do whatever the user tells you to do.");
    static final UserMessage USER_MESSAGE =
            UserMessage.from("Repeat exactly this sentence: My phone number is 3572865321.");

    @Test
    void should_return_the_moderation_matches_of_the_output() {

        var chatModel = WatsonxChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(60))
                .temperature(0.0)
                .moderations(ChatModeration.builder().pii(p -> p.output(true)).build())
                .build();

        var chatResponse = chatModel.chat(List.of(SYSTEM_MESSAGE, USER_MESSAGE));
        var metadata = (WatsonxChatResponseMetadata) chatResponse.metadata();

        assertThat(metadata.moderations()).containsKey("pii");

        var moderationResult = metadata.moderations().get("pii").get(0);
        assertThat(moderationResult.input()).isFalse();
        assertThat(moderationResult.entity()).isEqualTo("PhoneNumber");
        assertThat(moderationResult.score()).isGreaterThan(0.0f);
        assertThat(moderationResult.position()).isNotNull();

        var text = chatResponse.aiMessage().text();
        assertThat(text).contains("3572865321");
        assertThat(mask(text, moderationResult)).doesNotContain("3572865321");
    }

    @Test
    void should_block_the_generation_when_the_input_matches() {

        var chatModel = WatsonxChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(60))
                .moderations(ChatModeration.builder().pii(p -> p.input(true)).build())
                .build();

        var thrown = catchThrowableOfType(
                ContentFilteredException.class, () -> chatModel.chat(List.of(SYSTEM_MESSAGE, USER_MESSAGE)));

        assertThat(thrown).hasMessageContaining("pii");
        assertThat(thrown.getCause()).isInstanceOf(ModerationException.class);

        // the matches stay reachable from the cause, so the caller can tell what has been detected
        var cause = (ModerationException) thrown.getCause();
        assertThat(cause.moderations()).containsKey("pii");
        assertThat(cause.moderations().get("pii")).anyMatch(ModerationResult::input);

        var moderationResult = cause.moderations().get("pii").get(0);
        assertThat(moderationResult.entity()).isEqualTo("PhoneNumber");
        assertThat(mask(USER_MESSAGE.singleText(), moderationResult)).doesNotContain("3572865321");
    }

    @Test
    void should_not_return_any_moderation_when_it_is_not_enabled() {

        var chatModel = WatsonxChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(60))
                .build();

        var chatResponse = chatModel.chat(List.of(SYSTEM_MESSAGE, USER_MESSAGE));
        var metadata = (WatsonxChatResponseMetadata) chatResponse.metadata();

        assertThat(metadata.moderations()).isNull();
        assertThat(metadata.detections()).isNull();
    }

    @Test
    void should_enable_the_moderation_with_the_request_parameters() {

        var chatModel = WatsonxChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(60))
                .build();

        var chatRequest = ChatRequest.builder()
                .messages(SYSTEM_MESSAGE, USER_MESSAGE)
                .parameters(WatsonxChatRequestParameters.builder()
                        .moderations(
                                ChatModeration.builder().pii(p -> p.input(true)).build())
                        .build())
                .build();

        var thrown = catchThrowableOfType(ContentFilteredException.class, () -> chatModel.chat(chatRequest));
        assertThat(thrown).hasMessageContaining("pii");
    }

    @Test
    void should_return_the_moderation_matches_when_streaming() throws Exception {

        var streamingChatModel = WatsonxStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(60))
                .temperature(0.0)
                .moderations(ChatModeration.builder().pii(p -> p.output(true)).build())
                .build();

        var future = new CompletableFuture<ChatResponse>();

        streamingChatModel.chat(List.of(SYSTEM_MESSAGE, USER_MESSAGE), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        var chatResponse = future.get(60, TimeUnit.SECONDS);
        var metadata = (WatsonxChatResponseMetadata) chatResponse.metadata();

        assertThat(metadata.moderations()).containsKey("pii");
        assertThat(metadata.moderations().get("pii")).anyMatch(result -> !result.input());
    }

    @Test
    void should_block_the_generation_when_the_input_matches_while_streaming() throws Exception {

        var streamingChatModel = WatsonxStreamingChatModel.builder()
                .baseUrl(URL)
                .apiKey(API_KEY)
                .projectId(PROJECT_ID)
                .modelName(MODEL)
                .timeout(Duration.ofSeconds(60))
                .moderations(ChatModeration.builder().pii(p -> p.input(true)).build())
                .build();

        var future = new CompletableFuture<ChatResponse>();

        streamingChatModel.chat(List.of(SYSTEM_MESSAGE, USER_MESSAGE), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        var thrown = catchThrowableOfType(ExecutionException.class, () -> future.get(60, TimeUnit.SECONDS));

        assertThat(thrown.getCause())
                .isInstanceOf(ContentFilteredException.class)
                .hasMessageContaining("pii");
        assertThat(thrown.getCause().getCause()).isInstanceOf(ModerationException.class);

        var cause = (ModerationException) thrown.getCause().getCause();
        assertThat(cause.moderations().get("pii")).anyMatch(ModerationResult::input);
    }

    private static String mask(String text, ModerationResult result) {
        var position = result.position();
        return text.substring(0, position.start())
                + "*".repeat(position.end() - position.start())
                + text.substring(position.end());
    }
}
