package dev.langchain4j.model.output;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

class ResponseTest implements WithAssertions {
    @Test
    public void test_methods() {
        {
            Response<String> response = new Response<>("content");
            assertThat(response.content()).isEqualTo("content");
            assertThat(response.tokenUsage()).isNull();
            assertThat(response.finishReason()).isNull();
            assertThat(response).hasToString("Response { content = content, tokenUsage = null, finishReason = null, metadata = null }");
        }
        {
            TokenUsage tokenUsage = new TokenUsage(1, 2, 3);
            Response<String> response = new Response<>("content", tokenUsage, null);
            assertThat(response.content()).isEqualTo("content");
            assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
            assertThat(response.finishReason()).isNull();
            assertThat(response)
                    .hasToString(
                            "Response { " +
                            "content = content, tokenUsage = TokenUsage { " +
                            "inputTokenCount = 1, outputTokenCount = 2, totalTokenCount = 3 }, " +
                            "finishReason = null, metadata = null }");
        }
        {
            TokenUsage tokenUsage = new TokenUsage(1, 2, 3);
            Response<String> response = new Response<>("content", tokenUsage, FinishReason.LENGTH);
            assertThat(response.content()).isEqualTo("content");
            assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
            assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
            assertThat(response)
                    .hasToString(
                            "Response { " +
                                    "content = content, tokenUsage = TokenUsage { " +
                                    "inputTokenCount = 1, outputTokenCount = 2, totalTokenCount = 3 }, " +
                                    "finishReason = LENGTH, metadata = null }");
        }
        {
            TokenUsage tokenUsage = new TokenUsage(1, 2, 3);
            Map<String, Object> metadata = Collections.singletonMap("metaKey", "metaValue");
            Response<String> response = new Response<>("content", tokenUsage, FinishReason.LENGTH, metadata);
            assertThat(response.content()).isEqualTo("content");
            assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
            assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
            assertThat(response)
                    .hasToString(
                            "Response { " +
                                    "content = content, tokenUsage = TokenUsage { " +
                                    "inputTokenCount = 1, outputTokenCount = 2, totalTokenCount = 3 }, " +
                                    "finishReason = LENGTH, metadata = {metaKey=metaValue} }");
        }
    }

    @Test
    public void test_equals_hashCode() {
        String content1 = "content";
        String content2 = "changed";
        TokenUsage tokenUsage1 = new TokenUsage(1, 2, 3);
        TokenUsage tokenUsage2 = new TokenUsage(10, 2, 3);
        Map<String, Object> metadata1 = Collections.singletonMap("metaKay", "metaValue1");
        Map<String, Object> metadata1Same = Collections.singletonMap("metaKay", "metaValue1");
        Map<String, Object> metadata2 = Collections.singletonMap("metaKay", "metaValue2");

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
                .doesNotHaveSameHashCodeAs(new Response<>(content1, tokenUsage1, FinishReason.LENGTH))
                .isNotEqualTo(new Response<>(content1, null, null, metadata1))
                .doesNotHaveSameHashCodeAs(new Response<>(content1, null, null, metadata1));

        assertThat(new Response<>(content1, tokenUsage1, FinishReason.LENGTH))
                .isEqualTo(new Response<>(content1, tokenUsage1, FinishReason.LENGTH))
                .hasSameHashCodeAs(new Response<>(content1, tokenUsage1, FinishReason.LENGTH))
                .isNotEqualTo(new Response<>(content2, tokenUsage1, FinishReason.LENGTH))
                .isNotEqualTo(new Response<>(content1, tokenUsage2, FinishReason.LENGTH))
                .isNotEqualTo(new Response<>(content1, tokenUsage1, FinishReason.STOP));

        assertThat(new Response<>(content1, tokenUsage1, FinishReason.LENGTH, metadata1))
                .isEqualTo(new Response<>(content1, tokenUsage1, FinishReason.LENGTH, metadata1))
                .hasSameHashCodeAs(new Response<>(content1, tokenUsage1, FinishReason.LENGTH, metadata1))
                .isEqualTo(new Response<>(content1, tokenUsage1, FinishReason.LENGTH, metadata1Same))
                .hasSameHashCodeAs(new Response<>(content1, tokenUsage1, FinishReason.LENGTH, metadata1Same))
                .isNotEqualTo(new Response<>(content2, tokenUsage1, FinishReason.LENGTH, metadata2))
                .isNotEqualTo(new Response<>(content1, tokenUsage2, FinishReason.LENGTH, metadata1))
                .isNotEqualTo(new Response<>(content1, tokenUsage1, FinishReason.STOP, metadata1));
    }

    @Test
    public void test_builders() {
        assertThat(new Response<>("content"))
                .isEqualTo(Response.from("content"));

        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);

        assertThat(new Response<>("content", tokenUsage, null))
                .isEqualTo(Response.from("content", tokenUsage))
                .isEqualTo(Response.from("content", tokenUsage, null));

        assertThat(new Response<>("content", tokenUsage, FinishReason.LENGTH))
                .isEqualTo(Response.from("content", tokenUsage, FinishReason.LENGTH));

        Map<String, Object> metadata = Collections.singletonMap("metaKay", "metaValue");

        assertThat(new Response<>("content", tokenUsage, FinishReason.LENGTH, metadata))
                .isEqualTo(Response.from("content", tokenUsage, FinishReason.LENGTH, metadata));

    }
}