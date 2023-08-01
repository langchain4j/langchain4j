package dev.langchain4j.model.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BertTokenizerTest {

    BertTokenizer tokenizer = new BertTokenizer();

    @Test
    void should_count_tokens() {

        int tokenCount = tokenizer.countTokens("Hello, how are you doing?");

        assertThat(tokenCount).isEqualTo(7);
    }

    @Test
    void should_tokenize() {

        List<String> tokens = tokenizer.tokenize("Hello, how are you doing?");

        assertThat(tokens).containsExactly(
                "hello",
                ",",
                "how",
                "are",
                "you",
                "doing",
                "?"
        );
    }

    @Test
    void should_return_token_id() {

        assertThat(tokenizer.tokenId("[CLS]")).isEqualTo(101);
        assertThat(tokenizer.tokenId("[SEP]")).isEqualTo(102);
        assertThat(tokenizer.tokenId("hello")).isEqualTo(7592);
    }
}