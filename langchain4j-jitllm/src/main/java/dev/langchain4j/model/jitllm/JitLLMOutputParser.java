package dev.langchain4j.model.jitllm;

import java.util.List;
import java.util.function.Consumer;

/**
 * Splits generated text into the answer and the thinking enclosed in {@code <think>...</think>}, without the tags,
 * and cuts the answer at the first stop sequence.
 * <p>
 * Text can be fed in arbitrary chunks. A chunk that ends with the beginning of a tag or of a stop sequence is held
 * back until it can be decided, and whitespace at the start and at the end of the answer and of the thinking is
 * dropped, so the chunks passed to the consumers always add up to {@link #answer()} and {@link #thinking()}.
 */
final class JitLLMOutputParser {

    private static final String THINKING_START = "<think>";
    private static final String THINKING_END = "</think>";

    private final List<String> stopSequences;
    private final Consumer<String> answerConsumer;
    private final Consumer<String> thinkingConsumer;

    private final Segment answer = new Segment();
    private final Segment thinking = new Segment();

    private String pending = "";
    private String pendingAnswer = "";
    private boolean insideThinking;
    private boolean stopped;

    JitLLMOutputParser(List<String> stopSequences, Consumer<String> answerConsumer, Consumer<String> thinkingConsumer) {
        this.stopSequences = stopSequences;
        this.answerConsumer = answerConsumer;
        this.thinkingConsumer = thinkingConsumer;
    }

    void accept(String text) {
        pending += text;
        while (!stopped) {
            String tag = insideThinking ? THINKING_END : THINKING_START;
            int tagIndex = pending.indexOf(tag);
            if (tagIndex < 0) {
                int heldBack = lengthOfPrefixAtEnd(pending, List.of(tag));
                emit(pending.substring(0, pending.length() - heldBack), false);
                pending = pending.substring(pending.length() - heldBack);
                return;
            }
            emit(pending.substring(0, tagIndex), true);
            pending = pending.substring(tagIndex + tag.length());
            insideThinking = !insideThinking;
        }
    }

    void finish() {
        if (!stopped) {
            emit(pending, true);
        }
        pending = "";
    }

    String answer() {
        return answer.text.toString();
    }

    String thinking() {
        return thinking.text.isEmpty() ? null : thinking.text.toString();
    }

    private void emit(String text, boolean endOfSegment) {
        if (insideThinking) {
            thinking.append(text, thinkingConsumer);
        } else {
            emitAnswer(text, endOfSegment);
        }
    }

    private void emitAnswer(String text, boolean endOfSegment) {
        pendingAnswer += text;
        int stopIndex = indexOfFirstStopSequence(pendingAnswer);
        if (stopIndex >= 0) {
            answer.append(pendingAnswer.substring(0, stopIndex), answerConsumer);
            pendingAnswer = "";
            stopped = true;
            return;
        }
        int heldBack = endOfSegment ? 0 : lengthOfPrefixAtEnd(pendingAnswer, stopSequences);
        answer.append(pendingAnswer.substring(0, pendingAnswer.length() - heldBack), answerConsumer);
        pendingAnswer = pendingAnswer.substring(pendingAnswer.length() - heldBack);
    }

    private int indexOfFirstStopSequence(String text) {
        int first = -1;
        for (String stopSequence : stopSequences) {
            int index = text.indexOf(stopSequence);
            if (index >= 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        return first;
    }

    private static int lengthOfPrefixAtEnd(String text, List<String> sequences) {
        int longest = 0;
        for (String sequence : sequences) {
            for (int length = Math.min(text.length(), sequence.length() - 1); length > longest; length--) {
                if (text.endsWith(sequence.substring(0, length))) {
                    longest = length;
                    break;
                }
            }
        }
        return longest;
    }

    private static final class Segment {

        private final StringBuilder text = new StringBuilder();
        private String trailingWhitespace = "";

        private void append(String chunk, Consumer<String> consumer) {
            String candidate = text.isEmpty() ? chunk.stripLeading() : trailingWhitespace + chunk;
            String content = candidate.stripTrailing();
            trailingWhitespace = candidate.substring(content.length());
            if (!content.isEmpty()) {
                text.append(content);
                consumer.accept(content);
            }
        }
    }
}
