package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.assistant.SupportAssistant;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestModels.class)
class RandomPortHttpTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    SupportAssistant supportAssistant;

    @Test
    void does_not_capture_an_http_request_to_the_running_server_and_says_why() {
        String[] answer = new String[1];

        AiServiceCapture invocations = capture(
                () -> answer[0] = restTemplate.postForObject("/support/chat", "Please cancel MS-900", String.class));

        assertThat(answer[0]).isEqualTo("Done: Booking MS-900 cancelled");
        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured.")
                .hasMessageContaining("an HTTP request to a running server")
                .hasMessageContaining("thread that runs the lambda");
    }

    @Test
    void records_the_ai_service_bean_called_directly_from_a_random_port_test() {
        assertThat(capture(() -> supportAssistant.chat("Please cancel MS-901")).invocation())
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-901"));
    }
}
