package dev.langchain4j.http.client.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpErrorResponseReceivedTest {

    @Test
    void snapshots_headers_and_supports_case_insensitive_lookup() {
        List<String> values = new ArrayList<>(List.of("Bearer realm=\"test\""));
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("WWW-Authenticate", values);
        HttpErrorResponseReceived event = new HttpErrorResponseReceived(401, headers);

        values.clear();
        headers.clear();

        assertThat(event.statusCode()).isEqualTo(401);
        assertThat(event.headers().get("www-authenticate")).containsExactly("Bearer realm=\"test\"");
        assertThatThrownBy(() -> event.headers().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> event.headers().get("WWW-AUTHENTICATE").clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void header_case_does_not_change_equality_or_hash_code() {
        HttpErrorResponseReceived lower =
                new HttpErrorResponseReceived(404, Map.of("content-type", List.of("text/plain")));
        HttpErrorResponseReceived upper =
                new HttpErrorResponseReceived(404, Map.of("Content-Type", List.of("text/plain")));
        assertThat(lower).isEqualTo(upper);
        assertThat(lower.hashCode()).isEqualTo(upper.hashCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {99, 200, 204, 299, 600})
    void rejects_successful_or_invalid_status(int status) {
        assertThatThrownBy(() -> new HttpErrorResponseReceived(status, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
