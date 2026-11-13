package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

/**
 * The {@code doChat(ChatRequest)} hook never throws: an invalid request reaches the subscriber's {@code onError},
 * after {@code onSubscribe}. No request is sent, so no server is needed.
 */
class OpenAiStreamingPublisherHookTest {

    @Test
    void chat_completions_hook_should_signal_an_unsupported_parameter_instead_of_throwing() {

        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName(GPT_4_O_MINI)
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(OpenAiChatRequestParameters.builder()
                        .modelName(GPT_4_O_MINI.toString())
                        .topK(3)
                        .build())
                .build();

        List<String> signals = signals(model.doChat(request));

        assertThat(signals).containsExactly("onSubscribe", "onError: UnsupportedFeatureException");
    }

    @Test
    void responses_hook_should_signal_an_unsupported_parameter_instead_of_throwing() {

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName(GPT_4_O_MINI.toString())
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(OpenAiResponsesChatRequestParameters.builder()
                        .modelName(GPT_4_O_MINI.toString())
                        .topK(3)
                        .build())
                .build();

        List<String> signals = signals(model.doChat(request));

        assertThat(signals).containsExactly("onSubscribe", "onError: UnsupportedFeatureException");
    }

    private static List<String> signals(Flow.Publisher<ChatModelStreamingEvent> publisher) {
        List<String> signals = new CopyOnWriteArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                signals.add("onSubscribe");
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                signals.add("onNext");
            }

            @Override
            public void onError(Throwable error) {
                signals.add("onError: " + error.getClass().getSimpleName());
            }

            @Override
            public void onComplete() {
                signals.add("onComplete");
            }
        });
        return signals;
    }
}
