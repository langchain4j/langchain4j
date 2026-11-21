package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.mockito.Mockito.verify;

import com.example.support.assistant.SupportAssistant;
import com.example.support.booking.BookingTools;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@Import(TestModels.class)
class MockitoSpyBeanToolsTest {

    @MockitoSpyBean
    BookingTools bookingTools;

    @Autowired
    SupportAssistant supportAssistant;

    @Test
    void records_tools_of_a_spied_bean_and_mockito_sees_the_same_calls() {
        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("Please cancel MS-600")).invocation();

        assertThat(invocation)
                .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking")
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-600"));
        verify(bookingTools).cancelBooking("MS-600");
    }
}
