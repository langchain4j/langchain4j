package com.example.support;

import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.web.AsyncSupportService;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestModels.class)
class AsyncServiceTest {

    @Autowired
    AsyncSupportService asyncSupportService;

    @Test
    void does_not_capture_an_async_method_but_waits_for_its_future_and_says_why() {
        assertThat(AopUtils.isAopProxy(asyncSupportService)).isTrue();
        @SuppressWarnings("unchecked")
        CompletableFuture<String>[] future = new CompletableFuture[1];

        AiServiceCapture invocations = capture(() -> future[0] = asyncSupportService.ask("Please cancel MS-950"));

        assertThat(future[0]).isCompletedWithValue("Done: Booking MS-950 cancelled");
        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No AI Service invocation was captured.")
                .hasMessageContaining("an @Async method");
    }

    @Test
    void does_not_rethrow_the_failure_of_an_unrecorded_async_invocation_as_an_ai_service_failure() {
        // the future fails, capture(...) rethrows that failure: it is not an AI Service failure it recorded
        assertThatThrownBy(() -> capture(() -> asyncSupportService.ask("leak something")))
                .hasMessageContaining("The response leaks internal notes");
    }
}
