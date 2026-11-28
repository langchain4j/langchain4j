package dev.langchain4j.http.client.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class HttpResponseLoggerTest {

    private static final String SECRET = "sk-1234567890abcdefgh";
    private static final String MASKED_SECRET = "sk-12...gh";
    private static final String COMPLETION = "PRIVATE_COMPLETION";

    @Test
    void should_redact_textual_response_body_by_default() {
        String body = "{\"content\":\"" + COMPLETION + "\",\"api_key\":\"" + SECRET + "\"}";
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("application/json")))
                .body(body)
                .build();
        Logger log = mock(Logger.class);

        HttpResponseLogger.log(log, response);

        ArgumentCaptor<Object> arguments = ArgumentCaptor.forClass(Object.class);
        verify(log).info(anyString(), arguments.capture(), arguments.capture(), arguments.capture());
        verifyNoMoreInteractions(log);
        assertThat(arguments.getAllValues().get(0)).isEqualTo(200);
        assertThat(arguments.getAllValues().get(2))
                .isEqualTo("<" + body.getBytes(StandardCharsets.UTF_8).length + " bytes>");
        assertThat(arguments.getAllValues().toString())
                .doesNotContain(COMPLETION)
                .doesNotContain(SECRET);
    }

    @Test
    void should_mask_secret_fields_when_logging_full_payloads() {
        String body = "{\"content\":\"" + COMPLETION + "\",\"api_key\":\"" + SECRET + "\"}";
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of(
                        "Content-Type", List.of("application/json"), "Authorization", List.of("Bearer " + SECRET)))
                .body(body)
                .build();
        Logger log = mock(Logger.class);

        HttpResponseLogger.log(log, response, true);

        ArgumentCaptor<Object> arguments = ArgumentCaptor.forClass(Object.class);
        verify(log).info(anyString(), arguments.capture(), arguments.capture(), arguments.capture());
        verifyNoMoreInteractions(log);
        String loggedHeaders = (String) arguments.getAllValues().get(1);
        String loggedBody = (String) arguments.getAllValues().get(2);
        assertThat(loggedHeaders).doesNotContain(SECRET);
        assertThat(loggedBody)
                .isEqualTo("{\"content\":\"" + COMPLETION + "\",\"api_key\":\"" + MASKED_SECRET + "\"}")
                .contains(COMPLETION)
                .doesNotContain(SECRET);
    }

    @Test
    void should_keep_binary_body_summary_when_logging_full_payloads() {
        byte[] bytes = new byte[] {0x00, 0x01, 0x02, 0x03};
        SuccessfulHttpResponse response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("Content-Type", List.of("audio/mpeg")))
                .body(bytes)
                .build();
        Logger log = mock(Logger.class);

        HttpResponseLogger.log(log, response, true);

        ArgumentCaptor<Object> arguments = ArgumentCaptor.forClass(Object.class);
        verify(log).info(anyString(), arguments.capture(), arguments.capture(), arguments.capture());
        assertThat(arguments.getAllValues().get(2)).isEqualTo("[binary body, 4 bytes, content-type: audio/mpeg]");
    }
}
