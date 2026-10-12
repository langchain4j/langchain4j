package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class OpenAiImageTokenUsageTest {

    @Test
    void should_add_token_usages_with_details() {
        // given
        OpenAiImageTokenUsage tokenUsage1 = OpenAiImageTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(OpenAiImageTokenUsage.TokensDetails.builder()
                        .imageTokens(7)
                        .textTokens(3)
                        .build())
                .outputTokenCount(20)
                .outputTokensDetails(OpenAiImageTokenUsage.TokensDetails.builder()
                        .imageTokens(20)
                        .build())
                .totalTokenCount(30)
                .build();

        OpenAiImageTokenUsage tokenUsage2 = OpenAiImageTokenUsage.builder()
                .inputTokenCount(5)
                .inputTokensDetails(OpenAiImageTokenUsage.TokensDetails.builder()
                        .imageTokens(1)
                        .textTokens(4)
                        .build())
                .outputTokenCount(40)
                .outputTokensDetails(OpenAiImageTokenUsage.TokensDetails.builder()
                        .imageTokens(40)
                        .build())
                .totalTokenCount(45)
                .build();

        // when
        OpenAiImageTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result)
                .isEqualTo(OpenAiImageTokenUsage.builder()
                        .inputTokenCount(15)
                        .inputTokensDetails(OpenAiImageTokenUsage.TokensDetails.builder()
                                .imageTokens(8)
                                .textTokens(7)
                                .build())
                        .outputTokenCount(60)
                        .outputTokensDetails(OpenAiImageTokenUsage.TokensDetails.builder()
                                .imageTokens(60)
                                .build())
                        .totalTokenCount(75)
                        .build());
    }

    @Test
    void should_keep_details_when_only_one_side_has_them() {
        // given
        OpenAiImageTokenUsage.TokensDetails details = OpenAiImageTokenUsage.TokensDetails.builder()
                .imageTokens(7)
                .textTokens(3)
                .build();
        OpenAiImageTokenUsage tokenUsage1 = OpenAiImageTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(details)
                .build();
        OpenAiImageTokenUsage tokenUsage2 =
                OpenAiImageTokenUsage.builder().inputTokenCount(5).build();

        // when
        OpenAiImageTokenUsage result = tokenUsage1.add(tokenUsage2);

        // then
        assertThat(result.inputTokenCount()).isEqualTo(15);
        assertThat(result.inputTokensDetails()).isEqualTo(details);
        assertThat(result.outputTokensDetails()).isNull();
    }

    @Test
    void should_add_plain_token_usage() {
        // given
        OpenAiImageTokenUsage.TokensDetails details =
                OpenAiImageTokenUsage.TokensDetails.builder().imageTokens(7).build();
        OpenAiImageTokenUsage tokenUsage = OpenAiImageTokenUsage.builder()
                .inputTokenCount(10)
                .inputTokensDetails(details)
                .outputTokenCount(20)
                .totalTokenCount(30)
                .build();

        // when
        OpenAiImageTokenUsage result = tokenUsage.add(new TokenUsage(1, 2, 3));

        // then
        assertThat(result.inputTokenCount()).isEqualTo(11);
        assertThat(result.outputTokenCount()).isEqualTo(22);
        assertThat(result.totalTokenCount()).isEqualTo(33);
        assertThat(result.inputTokensDetails()).isEqualTo(details);
    }

    @Test
    void should_handle_null_token_usage_when_adding() {
        // given
        OpenAiImageTokenUsage tokenUsage =
                OpenAiImageTokenUsage.builder().inputTokenCount(10).build();

        // when
        OpenAiImageTokenUsage result = tokenUsage.add(null);

        // then
        assertThat(result).isEqualTo(tokenUsage);
    }
}
