package com.example.support;

import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.example.support.assistant.SupportAssistant;
import dev.langchain4j.test.service.AiServiceCapture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestModels.class)
class MockitoBeanAssistantTest {

    @MockitoBean
    SupportAssistant supportAssistant;

    @Autowired
    MockMvc mockMvc;

    @Test
    void does_not_capture_a_mocked_ai_service_and_says_why() {
        when(supportAssistant.chat(anyString())).thenReturn("mocked answer");

        AiServiceCapture invocations = capture(() -> mockMvc.perform(
                                post("/support/chat").contentType("text/plain").content("Hi"))
                        .andExpect(content().string("mocked answer"))
                        .andReturn());

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No AI Service invocation was captured")
                .hasMessageContaining("@MockitoBean");
    }
}
