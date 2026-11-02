package dev.langchain4j.test.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgeResponseTest {

    private static final JudgeVerdict VERDICT = JudgeVerdict.from(JudgeCriterion.from("is polite"), true, "Yes.");

    @Test
    void should_create_response_without_token_usage() {
        JudgeResponse response = JudgeResponse.from(List.of(VERDICT));

        assertThat(response.verdicts()).containsExactly(VERDICT);
        assertThat(response.tokenUsage()).isNull();
    }

    @Test
    void should_create_response_with_token_usage() {
        JudgeResponse response = JudgeResponse.builder()
                .verdicts(List.of(VERDICT))
                .tokenUsage(new TokenUsage(10, 5))
                .build();

        assertThat(response.verdicts()).containsExactly(VERDICT);
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(10, 5));
    }

    @Test
    void should_copy_verdicts() {
        List<JudgeVerdict> verdicts = new ArrayList<>(List.of(VERDICT));

        JudgeResponse response = JudgeResponse.from(verdicts);
        verdicts.clear();

        assertThat(response.verdicts()).containsExactly(VERDICT);
        assertThatThrownBy(() -> response.verdicts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_fail_without_verdicts() {
        assertThatThrownBy(() -> JudgeResponse.from(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verdicts");
        assertThatThrownBy(() -> JudgeResponse.builder().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verdicts");
    }

    @Test
    void should_fail_with_null_verdict() {
        assertThatThrownBy(() -> JudgeResponse.from(Arrays.asList(VERDICT, null)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verdict 2 cannot be null");
    }

    @Test
    void should_implement_equals_hash_code_and_to_string() {
        JudgeResponse response = JudgeResponse.builder()
                .verdicts(List.of(VERDICT))
                .tokenUsage(new TokenUsage(10, 5))
                .build();
        JudgeResponse same = JudgeResponse.builder()
                .verdicts(List.of(VERDICT))
                .tokenUsage(new TokenUsage(10, 5))
                .build();

        assertThat(response).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(response).isNotEqualTo(JudgeResponse.from(List.of(VERDICT)));
        assertThat(response.toString()).startsWith("JudgeResponse{verdicts=[").contains("tokenUsage=");
    }
}
