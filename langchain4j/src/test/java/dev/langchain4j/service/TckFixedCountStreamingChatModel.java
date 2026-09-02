package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import java.util.concurrent.Flow;
import mutiny.zero.BackpressureStrategy;
import mutiny.zero.TubeConfiguration;
import mutiny.zero.ZeroPublisher;

/**
 * A deterministic, <b>re-subscribable</b> {@link StreamingChatModel} for the Reactive Streams TCK: each
 * subscription emits exactly {@code partialResponses} {@link PartialResponse}s followed by a single terminal
 * {@link ChatResponse} (or fails immediately when {@code fail} is set). Emission runs on a fresh daemon thread so
 * the stream is genuinely asynchronous. Unlike {@code StreamingEventChatModelMock} it does not consume a queue, so
 * it can be subscribed any number of times — as the TCK requires.
 */
final class TckFixedCountStreamingChatModel implements StreamingChatModel {

    private final int partialResponses;
    private final boolean fail;

    private TckFixedCountStreamingChatModel(int partialResponses, boolean fail) {
        this.partialResponses = partialResponses;
        this.fail = fail;
    }

    static TckFixedCountStreamingChatModel emitting(int partialResponses) {
        return new TckFixedCountStreamingChatModel(partialResponses, false);
    }

    static TckFixedCountStreamingChatModel failing() {
        return new TckFixedCountStreamingChatModel(0, true);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        TubeConfiguration config = new TubeConfiguration()
                .withBackpressureStrategy(BackpressureStrategy.BUFFER)
                .withBufferSize(Math.max(256, partialResponses + 8));

        return ZeroPublisher.create(config, tube -> {
            Thread thread = new Thread(() -> {
                if (fail) {
                    tube.fail(new RuntimeException("boom"));
                    return;
                }
                for (int i = 0; i < partialResponses; i++) {
                    if (tube.cancelled()) {
                        return;
                    }
                    tube.send(new PartialResponse("x"));
                }
                if (tube.cancelled()) {
                    return;
                }
                ChatResponse response = ChatResponse.builder()
                        .aiMessage(AiMessage.from(partialResponses == 0 ? "" : "x".repeat(partialResponses)))
                        .build();
                tube.send(new CompleteResponse(response));
                tube.complete();
            });
            thread.setDaemon(true);
            thread.start();
        });
    }
}
