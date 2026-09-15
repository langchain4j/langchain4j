package dev.langchain4j.model.google.genai;

import com.google.genai.types.Blob;
import com.google.genai.types.LiveServerContent;
import com.google.genai.types.LiveServerMessage;
import com.google.genai.types.Part;
import com.google.genai.types.Transcription;
import com.google.genai.types.UsageMetadata;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Routes a {@link LiveServerMessage} to the matching {@link GoogleGenAiLiveResponseHandler} callbacks.
 *
 * <p>{@link #dispatch(LiveServerMessage)} is {@code synchronized}, since the SDK does not guarantee that
 * callbacks run on a single thread; this also gives the accumulated turn text a consistent memory view. A
 * single message can carry several independent fields, so every field is checked; they are not mutually
 * exclusive.
 */
class GoogleGenAiLiveEventDispatcher {

    private final GoogleGenAiLiveResponseHandler handler;
    private final StringBuilder currentTurnText = new StringBuilder();

    GoogleGenAiLiveEventDispatcher(GoogleGenAiLiveResponseHandler handler) {
        this.handler = handler;
    }

    synchronized void dispatch(LiveServerMessage message) {
        try {
            message.serverContent().ifPresent(this::dispatchServerContent);
            message.usageMetadata().ifPresent(usage -> handler.onUsageMetadata(toTokenUsage(usage)));
            message.goAway()
                    .ifPresent(goAway -> handler.onGoAway(goAway.timeLeft().orElse(null)));
            message.sessionResumptionUpdate()
                    .flatMap(update -> update.newHandle())
                    .ifPresent(handler::onSessionResumptionUpdate);
        } catch (RuntimeException e) {
            handler.onError(e);
        }
    }

    private void dispatchServerContent(LiveServerContent serverContent) {
        if (serverContent.interrupted().orElse(false)) {
            currentTurnText.setLength(0);
            handler.onInterrupted();
        }

        serverContent
                .modelTurn()
                .flatMap(content -> content.parts())
                .ifPresent(parts -> parts.forEach(this::dispatchPart));

        serverContent.inputTranscription().flatMap(Transcription::text).ifPresent(handler::onInputTranscription);
        serverContent.outputTranscription().flatMap(Transcription::text).ifPresent(handler::onOutputTranscription);

        if (serverContent.generationComplete().orElse(false)) {
            handler.onGenerationComplete();
        }

        if (serverContent.turnComplete().orElse(false)) {
            if (currentTurnText.length() > 0) {
                String completeText = currentTurnText.toString();
                currentTurnText.setLength(0);
                handler.onCompleteText(completeText);
            }
            handler.onTurnComplete();
        }
    }

    private void dispatchPart(Part part) {
        part.text().ifPresent(text -> {
            currentTurnText.append(text);
            handler.onPartialText(text);
        });
        part.inlineData().flatMap(Blob::data).ifPresent(handler::onAudio);
    }

    private static TokenUsage toTokenUsage(UsageMetadata usage) {
        return new TokenUsage(
                usage.promptTokenCount().orElse(null),
                usage.responseTokenCount().orElse(null),
                usage.totalTokenCount().orElse(null));
    }
}
