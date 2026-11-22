package dev.langchain4j.store.embedding.vespa;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.Headers;
import org.junit.jupiter.api.Test;

class VespaLoggingInterceptorTest {

    @Test
    void should_mask_secret_headers() {
        Headers headers = new Headers.Builder()
                .add("Authorization", "Bearer secret-token")
                .add("X-Api-Key", "secret-api-key")
                .add("Cookie", "session=secret-cookie")
                .add("Content-Type", "application/json")
                .build();

        String logged = VespaResponseLoggingInterceptor.getHeaders(headers);

        assertThat(logged)
                .doesNotContain("secret")
                .contains("[Authorization: ***]")
                .contains("[X-Api-Key: ***]")
                .contains("[Cookie: ***]")
                .contains("[Content-Type: application/json]");
    }
}
