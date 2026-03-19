package dev.langchain4j.model.minimax;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MiniMaxChatModelNameTest {

    @Test
    void should_return_correct_string_for_m2_7() {
        assertThat(MiniMaxChatModelName.MINIMAX_M2_7.toString()).isEqualTo("MiniMax-M2.7");
    }

    @Test
    void should_return_correct_string_for_m2_7_highspeed() {
        assertThat(MiniMaxChatModelName.MINIMAX_M2_7_HIGHSPEED.toString()).isEqualTo("MiniMax-M2.7-highspeed");
    }

    @Test
    void should_have_expected_number_of_values() {
        assertThat(MiniMaxChatModelName.values()).hasSize(2);
    }
}
