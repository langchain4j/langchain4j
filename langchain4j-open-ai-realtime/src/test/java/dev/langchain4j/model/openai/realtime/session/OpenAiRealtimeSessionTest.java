package dev.langchain4j.model.openai.realtime.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiRealtimeSessionTest {

    private OpenAiRealtimeSession session;

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void connect_setsAuthorizationHeader() {
        FakeRealtimeTransport transport = new FakeRealtimeTransport();
        session = OpenAiRealtimeSession.builder()
                .transport(transport)
                .apiKey("sk-test")
                .model("gpt-realtime-2.1")
                .listener(json -> {})
                .safetyIdentifier("safety-1")
                .build();

        session.connect();

        assertThat(transport.connectedUrl).isEqualTo("wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1");
        assertThat(transport.connectedHeaders)
                .containsEntry("Authorization", "Bearer sk-test")
                .containsEntry("OpenAI-Safety-Identifier", "safety-1");
    }

    @Test
    void sendEvent_isSerializedOnSingleWriter() throws Exception {
        FakeRealtimeTransport transport = new FakeRealtimeTransport();
        session = OpenAiRealtimeSession.builder()
                .transport(transport)
                .apiKey("sk-test")
                .model("gpt-realtime-2.1")
                .listener(json -> {})
                .build();
        session.connect();

        String event1 = "{\"type\":\"session.update\",\"session\":{\"type\":\"realtime\"}}";
        String event2 = "{\"type\":\"response.create\"}";
        String event3 = "{\"type\":\"input_audio_buffer.commit\"}";
        session.sendEvent(event1);
        session.sendEvent(event2);
        session.sendEvent(event3);

        awaitSent(transport, 3);
        assertThat(transport.sent).containsExactly(event1, event2, event3);
    }

    @Test
    void inboundServerMessage_notifiesListener() {
        FakeRealtimeTransport transport = new FakeRealtimeTransport();
        List<String> events = new CopyOnWriteArrayList<>();
        session = OpenAiRealtimeSession.builder()
                .transport(transport)
                .apiKey("sk-test")
                .model("gpt-realtime-2.1")
                .listener(events::add)
                .build();
        session.connect();

        String json = "{\"type\":\"session.created\"}";
        transport.simulateText(json);

        assertThat(events).containsExactly(json);
    }

    private static void awaitSent(FakeRealtimeTransport transport, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (transport.sent.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(transport.sent).hasSize(expected);
    }
}
