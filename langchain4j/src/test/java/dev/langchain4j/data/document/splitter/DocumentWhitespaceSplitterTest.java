package dev.langchain4j.data.document.splitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DocumentWhitespaceSplitterTest {

    static Stream<HierarchicalDocumentSplitter> splitters() {
        return Stream.of(new DocumentByLineSplitter(100, 0), new DocumentByParagraphSplitter(100, 0));
    }

    @ParameterizedTest
    @MethodSource("splitters")
    void should_not_rescan_long_whitespace_runs_without_a_separator(HierarchicalDocumentSplitter splitter) {
        String text = "before" + " ".repeat(80_000) + "after";

        // Non-preemptive: do not leave a regex running in a background thread if this regresses.
        assertTimeout(
                Duration.ofSeconds(2), () -> assertThat(splitter.split(text)).containsExactly(text));
    }

    @ParameterizedTest
    @MethodSource("splitters")
    void should_preserve_whitespace_and_line_break_semantics(HierarchicalDocumentSplitter splitter) {
        String originalRegex =
                splitter instanceof DocumentByLineSplitter ? "\\s*\\R\\s*" : "\\s*(?>\\R)\\s*(?>\\R)\\s*";
        char[] alphabet = {'a', 'b', ' ', '\t', '\n', '\r', '\u000b', '\f', '\u0085', '\u2028', '\u2029'};
        Random random = new Random(42);
        for (int sample = 0; sample < 1_000; sample++) {
            StringBuilder text = new StringBuilder();
            for (int remaining = random.nextInt(35); remaining > 0; remaining--) {
                text.append(alphabet[random.nextInt(alphabet.length)]);
            }
            assertThat(splitter.split(text.toString()))
                    .containsExactly(text.toString().split(originalRegex));
        }
    }
}
