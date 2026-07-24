package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.google.genai.AsyncSession;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.LiveSendClientContentParameters;
import com.google.genai.types.LiveSendRealtimeInputParameters;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class GoogleGenAiLiveSessionImpl implements GoogleGenAiLiveSession {

    private final AsyncSession session;
    private final GoogleGenAiLiveResponseHandler handler;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    GoogleGenAiLiveSessionImpl(AsyncSession session, GoogleGenAiLiveResponseHandler handler) {
        this.session = session;
        this.handler = handler;
        session.receive(new GoogleGenAiLiveEventDispatcher(handler)::dispatch);
    }

    @Override
    public void sendText(String text) {
        ensureOpen();
        ensureNotNull(text, "text");
        session.sendRealtimeInput(realtimeText(text)).exceptionally(this::onSendError);
    }

    @Override
    public void sendClientContent(List<ChatMessage> messages, boolean turnComplete) {
        ensureOpen();
        ensureNotNull(messages, "messages");
        session.sendClientContent(clientContent(messages, turnComplete)).exceptionally(this::onSendError);
    }

    @Override
    public void sendAudio(byte[] audioData, String mimeType) {
        ensureOpen();
        ensureNotNull(audioData, "audioData");
        ensureNotBlank(mimeType, "mimeType");
        session.sendRealtimeInput(realtimeAudio(audioData, mimeType)).exceptionally(this::onSendError);
    }

    @Override
    public void sendAudioStreamEnd() {
        ensureOpen();
        session.sendRealtimeInput(realtimeAudioStreamEnd()).exceptionally(this::onSendError);
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    @Override
    public String sessionId() {
        return session.sessionId();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                session.close().join();
            } catch (RuntimeException e) {
                handler.onError(GoogleGenAiExceptionMapper.INSTANCE.mapException(e));
            } finally {
                handler.onClose();
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("The live session is closed");
        }
    }

    private Void onSendError(Throwable t) {
        handler.onError(GoogleGenAiExceptionMapper.INSTANCE.mapException(t));
        return null;
    }

    static LiveSendRealtimeInputParameters realtimeText(String text) {
        return LiveSendRealtimeInputParameters.builder().text(text).build();
    }

    static LiveSendRealtimeInputParameters realtimeAudio(byte[] audioData, String mimeType) {
        return LiveSendRealtimeInputParameters.builder()
                .audio(Blob.builder().data(audioData).mimeType(mimeType).build())
                .build();
    }

    static LiveSendRealtimeInputParameters realtimeAudioStreamEnd() {
        return LiveSendRealtimeInputParameters.builder().audioStreamEnd(true).build();
    }

    static LiveSendClientContentParameters clientContent(List<ChatMessage> messages, boolean turnComplete) {
        List<Content> turns = GoogleGenAiContentMapper.toContents(messages);
        return LiveSendClientContentParameters.builder()
                .turns(turns)
                .turnComplete(turnComplete)
                .build();
    }
}
