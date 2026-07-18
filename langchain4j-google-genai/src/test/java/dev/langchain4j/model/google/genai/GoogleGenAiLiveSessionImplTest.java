package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.genai.AsyncSession;
import com.google.genai.types.LiveSendRealtimeInputParameters;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleGenAiLiveSessionImplTest {

    private final AsyncSession session = mock(AsyncSession.class);
    private final GoogleGenAiLiveResponseHandler handler = mock(GoogleGenAiLiveResponseHandler.class);
    private GoogleGenAiLiveSessionImpl liveSession;

    @BeforeEach
    void setUp() {
        when(session.receive(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(session.sendRealtimeInput(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(session.close()).thenReturn(CompletableFuture.completedFuture(null));
        liveSession = new GoogleGenAiLiveSessionImpl(session, handler);
    }

    @Test
    void send_text_forwards_realtime_input_to_the_session() {
        liveSession.sendText("hello");

        ArgumentCaptor<LiveSendRealtimeInputParameters> captor =
                ArgumentCaptor.forClass(LiveSendRealtimeInputParameters.class);
        verify(session).sendRealtimeInput(captor.capture());
        assertThat(captor.getValue().text()).contains("hello");
    }

    @Test
    void close_is_idempotent_and_notifies_the_handler_once() {
        liveSession.close();
        liveSession.close();

        assertThat(liveSession.isOpen()).isFalse();
        verify(session, times(1)).close();
        verify(handler, times(1)).onClose();
    }

    @Test
    void sending_after_close_throws() {
        liveSession.close();

        assertThatThrownBy(() -> liveSession.sendText("hi")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> liveSession.sendAudio(new byte[] {1}, "audio/pcm;rate=16000"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void send_rejects_invalid_arguments() {
        assertThatThrownBy(() -> liveSession.sendText(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> liveSession.sendAudio(null, "audio/pcm;rate=16000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> liveSession.sendAudio(new byte[] {1}, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
