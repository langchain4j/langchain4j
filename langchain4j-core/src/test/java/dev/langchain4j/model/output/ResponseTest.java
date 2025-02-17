package dev.langchain4j.model.output;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ResponseTest implements WithAssertions {
    @Test
    void methods() {
        {
            Response<String> response = new Response<>("content");
            assertThat(response.content()).isEqualTo("content");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
            assertThat(response)
                    .hasToString(
                            "Response { content = content, tokenUsage = null, finishReason = null, metadata = {} }");
        }
        {
            TokenUsage tokenUsage = new TokenUsage(1, 2, 3);
            Response<String> response = new Response<>("content", tokenUsage, null);
            assertThat(response.content()).isEqualTo("content");
            assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
            assertThat(response.finishReason()).isNull();
            assertThat(response)
                    .hasToString("Response { " + "content = content, tokenUsage = TokenUsage { "
                            + "inputTokenCount = 1, outputTokenCount = 2, totalTokenCount = 3 }, "
                            + "finishReason = null, "
                            + "metadata = {} }");
        }
        {
            TokenUsage tokenUsage = new TokenUsage(1, 2, 3);
            Response<String> response = new Response<>("content", tokenUsage, FinishReason.LENGTH);
            assertThat(response.content()).isEqualTo("content");
            assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
            assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
            assertThat(response)
                    .hasToString("Response { " + "content = content, tokenUsage = TokenUsage { "
                            + "inputTokenCount = 1, outputTokenCount = 2, totalTokenCount = 3 }, "
                            + "finishReason = LENGTH, "
                            + "metadata = {} }");
        }
    }

    @Test
    void equals_hash_code() {
        String content1 = "content";
        String content2 = "changed";
        TokenUsage tokenUsage1 = new TokenUsage(1, 2, 3);
        TokenUsage tokenUsage2 = new TokenUsage(10, 2, 3);

        assertThat(new Response<>(content1, null, null))
                .isEqualTo(new Response<>(content1, null, null))
                .hasSameHashCodeAs(new Response<>(content1, null, null))
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isNotEqualTo(new Response<>(content2, null, null))
                .doesNotHaveSameHashCodeAs(new Response<>(content2, null, null))
                .isNotEqualTo(new Response<>(content1, tokenUsage1, null))
                .doesNotHaveSameHashCodeAs(new Response<>(content1, tokenUsage1, null))
                .isNotEqualTo(new Response<>(content1, null, FinishReason.LENGTH))
                .doesNotHaveSameHashCodeAs(new Response<>(content1, null, FinishReason.LENGTH))
                .isNotEqualTo(new Response<>(content1, tokenUsage1, FinishReason.LENGTH))
                .doesNotHaveSameHashCodeAs(new Response<>(content1, tokenUsage1, FinishReason.LENGTH));

        assertThat(new Response<>(content1, tokenUsage1, FinishReason.LENGTH))
                .isEqualTo(new Response<>(content1, tokenUsage1, FinishReason.LENGTH))
                .hasSameHashCodeAs(new Response<>(content1, tokenUsage1, FinishReason.LENGTH))
                .isNotEqualTo(new Response<>(content2, tokenUsage1, FinishReason.LENGTH))
                .isNotEqualTo(new Response<>(content1, tokenUsage2, FinishReason.LENGTH))
                .isNotEqualTo(new Response<>(content1, tokenUsage1, FinishReason.STOP));
    }

    @Test
    void builders() {
        assertThat(new Response<>("content")).isEqualTo(Response.from("content"));

        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        assertThat(new Response<>("content", tokenUsage, null))
                .isEqualTo(Response.from("content", tokenUsage))
                .isEqualTo(Response.from("content", tokenUsage, null));

        assertThat(new Response<>("content", tokenUsage, FinishReason.LENGTH))
                .isEqualTo(Response.from("content", tokenUsage, FinishReason.LENGTH));
    }
}
