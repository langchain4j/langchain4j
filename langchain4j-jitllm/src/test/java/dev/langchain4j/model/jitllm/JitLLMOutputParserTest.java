package dev.langchain4j.model.jitllm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JitLLMOutputParserTest {

    private final List<String> answerChunks = new ArrayList<>();
    private final List<String> thinkingChunks = new ArrayList<>();

    private JitLLMOutputParser parse(List<String> stopSequences, String... chunks) {
        JitLLMOutputParser parser = new JitLLMOutputParser(stopSequences, answerChunks::add, thinkingChunks::add);
        for (String chunk : chunks) {
            parser.accept(chunk);
        }
        parser.finish();
        assertThat(String.join("", answerChunks)).isEqualTo(parser.answer());
        assertThat(thinkingChunks.isEmpty() ? null : String.join("", thinkingChunks))
                .isEqualTo(parser.thinking());
        return parser;
    }

    private JitLLMOutputParser parse(String... chunks) {
        return parse(List.of(), chunks);
    }

    @Test
    void should_separate_thinking_from_answer_without_tags() {

        JitLLMOutputParser parser = parse("<think>\nThe user asks.\n</think>\n\nBerlin");

        assertThat(parser.thinking()).isEqualTo("The user asks.");
        assertThat(parser.answer()).isEqualTo("Berlin");
    }

    @Test
    void should_return_null_thinking_when_thinking_is_empty() {

        JitLLMOutputParser parser = parse("<think>\n\n</think>\n\nBerlin");

        assertThat(parser.thinking()).isNull();
        assertThat(parser.answer()).isEqualTo("Berlin");
    }

    @Test
    void should_keep_text_without_thinking_as_answer() {

        JitLLMOutputParser parser = parse("  line1\n\n  indented  \n");

        assertThat(parser.thinking()).isNull();
        assertThat(parser.answer()).isEqualTo("line1\n\n  indented");
    }

    @Test
    void should_treat_unfinished_thinking_as_thinking_when_generation_was_cut_off() {

        JitLLMOutputParser parser = parse("<think>\nOkay, the user");

        assertThat(parser.thinking()).isEqualTo("Okay, the user");
        assertThat(parser.answer()).isEmpty();
    }

    @Test
    void should_detect_tags_split_across_chunks() {

        JitLLMOutputParser parser = parse("<th", "ink>reason", "ing</th", "ink>\nBer", "lin <", "b>");

        assertThat(parser.thinking()).isEqualTo("reasoning");
        assertThat(parser.answer()).isEqualTo("Berlin <b>");
    }

    @Test
    void should_stream_chunks_without_surrounding_whitespace() {

        JitLLMOutputParser parser =
                parse("<think>", "\n", "hmm", "\n", "</think>", "\n\n", "Hello", " ", "world", "\n");

        assertThat(parser.thinking()).isEqualTo("hmm");
        assertThat(parser.answer()).isEqualTo("Hello world");
    }

    @Test
    void should_not_split_characters_outside_the_basic_multilingual_plane() {

        parse("Hi ", "😀", " there");

        assertThat(answerChunks).containsExactly("Hi", " 😀", " there");
    }

    @Test
    void should_cut_answer_at_stop_sequence_split_across_chunks() {

        JitLLMOutputParser parser = parse(List.of("World"), "Hello ", "Wor", "ld!", " More");

        assertThat(parser.answer()).isEqualTo("Hello");
        assertThat(answerChunks).containsExactly("Hello");
    }

    @Test
    void should_release_held_back_text_that_is_not_a_stop_sequence() {

        JitLLMOutputParser parser = parse(List.of("World"), "Hello ", "Wo", "nderful");

        assertThat(parser.answer()).isEqualTo("Hello Wonderful");
    }

    @Test
    void should_not_apply_stop_sequences_to_thinking() {

        JitLLMOutputParser parser = parse(List.of("World"), "<think>say Hello World</think>", "Hello World");

        assertThat(parser.thinking()).isEqualTo("say Hello World");
        assertThat(parser.answer()).isEqualTo("Hello");
    }
}
