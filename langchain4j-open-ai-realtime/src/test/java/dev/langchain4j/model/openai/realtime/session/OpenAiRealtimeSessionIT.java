package dev.langchain4j.model.openai.realtime.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Short live session against OpenAI Realtime. Skipped when {@code OPENAI_API_KEY} is unset.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiRealtimeSessionIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void connects_and_receivesSessionCreated_thenSessionUpdated() throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        CountDownLatch sessionCreated = new CountDownLatch(1);
        CountDownLatch sessionUpdated = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

        OpenAiRealtimeSession session = OpenAiRealtimeSession.builder()
                .transport(new OkHttpRealtimeTransport())
                .apiKey(apiKey)
                .listener(new OpenAiRealtimeSessionListener() {
                    @Override
                    public void onEvent(String json) {
                        events.add(json);
                        try {
                            String type =
                                    OBJECT_MAPPER.readTree(json).path("type").asText();
                            if ("session.created".equals(type)) {
                                sessionCreated.countDown();
                            } else if ("session.updated".equals(type)) {
                                sessionUpdated.countDown();
                            }
                        } catch (Exception ignored) {
                            // keep collecting raw events
                        }
                    }

                    @Override
                    public void onClosed(int code, String reason) {
                        // no-op
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        failure.set(t);
                        sessionCreated.countDown();
                        sessionUpdated.countDown();
                    }
                })
                .build();

        try {
            session.connect();
            assertThat(sessionCreated.await(30, TimeUnit.SECONDS))
                    .as("session.created; failure=%s events=%s", failure.get(), events)
                    .isTrue();
            assertThat(failure.get()).isNull();

            session.sendEvent("{\"type\":\"session.update\",\"session\":{\"type\":\"realtime\","
                    + "\"instructions\":\"Say hi in one short sentence.\"}}");

            assertThat(sessionUpdated.await(30, TimeUnit.SECONDS))
                    .as("session.updated; failure=%s events=%s", failure.get(), events)
                    .isTrue();
            assertThat(failure.get()).isNull();

            boolean sawUpdated = events.stream().anyMatch(e -> {
                try {
                    JsonNode root = OBJECT_MAPPER.readTree(e);
                    return "session.updated".equals(root.path("type").asText());
                } catch (Exception ex) {
                    return false;
                }
            });
            assertThat(sawUpdated).isTrue();
        } finally {
            session.close();
        }
    }
}
