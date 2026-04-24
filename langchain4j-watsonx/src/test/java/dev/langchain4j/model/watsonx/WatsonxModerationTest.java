package dev.langchain4j.model.watsonx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.detection.DetectionResponse;
import com.ibm.watsonx.ai.detection.DetectionService;
import com.ibm.watsonx.ai.detection.DetectionTextRequest;
import com.ibm.watsonx.ai.detection.DetectionTextResponse;
import com.ibm.watsonx.ai.detection.detector.GraniteGuardian;
import com.ibm.watsonx.ai.detection.detector.Pii;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.model.moderation.ModerationModel;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
public class WatsonxModerationTest {

    @Mock
    DetectionService mockDetectionService;

    @Captor
    ArgumentCaptor<DetectionTextRequest> detectionTextRequest;

    @Mock
    DetectionService.Builder mockDetectionServiceBuilder;

    @BeforeEach
    void setUp() {
        when(mockDetectionServiceBuilder.baseUrl(any(URI.class))).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.projectId(any())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.spaceId(any())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.timeout(any())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.version(any())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.logRequests(any())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.logResponses(any())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.apiKey(any())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.httpClient(any())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.verifySsl(anyBoolean())).thenReturn(mockDetectionServiceBuilder);
        when(mockDetectionServiceBuilder.build()).thenReturn(mockDetectionService);
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void should_throw_an_illegal_argument_exception() {

        var ex = assertThrows(IllegalArgumentException.class, () -> WatsonxModerationModel.builder()
                .baseUrl("https://test.com")
                .apiKey("api-key-test")
                .projectId("project-id")
                .build());

        assertEquals("At least one detector must be provided", ex.getMessage());

        ex = assertThrows(IllegalArgumentException.class, () -> WatsonxModerationModel.builder()
                .baseUrl("https://test.com")
                .apiKey("api-key-test")
                .projectId("project-id")
                .detectors(List.of())
                .build());

        assertEquals("At least one detector must be provided", ex.getMessage());
    }

    @Test
    void should_flag_content_and_include_metadata_from_detection() {

        var response = new DetectionTextResponse("input", "Pii", "xxx", 0.3, 0, 5);

        when(mockDetectionService.detect(detectionTextRequest.capture()))
                .thenReturn(new DetectionResponse<>(List.of(response)));

        withDetectionServiceMock(() -> {
            ModerationModel model = WatsonxModerationModel.builder()
                    .baseUrl("https://test.com")
                    .apiKey("api-key")
                    .projectId("project-id")
                    .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
                    .build();

            var moderateResponse = model.moderate("input");

            var content = moderateResponse.content();
            assertEquals("input", content.flaggedText());
            assertTrue(content.flagged());

            var metadata = moderateResponse.metadata();
            assertEquals("xxx", metadata.get("detection"));
            assertEquals("Pii", metadata.get("detection_type"));
            assertEquals(5, metadata.get("end"));
            assertEquals(0, metadata.get("start"));
            assertEquals(0.3, metadata.get("score"));

            assertEquals("input", detectionTextRequest.getValue().input());
            assertEquals(2, detectionTextRequest.getValue().detectors().size());
        });
    }

    @Test
    void should_return_one_of_the_flagged_response() {

        var response_1 = new DetectionTextResponse("input", "Pii", "xxx", 0.3, 0, 5);
        var response_2 = new DetectionTextResponse("input1", "Pii", "xxx", 0.3, 0, 5);

        when(mockDetectionService.detect(detectionTextRequest.capture()))
                .thenAnswer(invocation -> new DetectionResponse<>(List.of()))
                .thenAnswer(invocation -> new DetectionResponse<>(List.of()))
                .thenAnswer(invocation -> new DetectionResponse<>(List.of(response_2)))
                .thenAnswer(invocation -> new DetectionResponse<>(List.of(response_1)));

        withDetectionServiceMock(() -> {
            ModerationModel model = WatsonxModerationModel.builder()
                    .baseUrl("https://test.com")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
                    .build();

            var moderateResponse = model.moderate(List.of(
                    SystemMessage.from("systemMessage"),
                    UserMessage.from("userMessage"),
                    AiMessage.from("aiMessage"),
                    ToolExecutionResultMessage.from("id", "toolName", "toolExecutionResult")));

            verify(mockDetectionService, atMost(4)).detect(any());
            verify(mockDetectionService, atLeast(1)).detect(any());
            int calls = detectionTextRequest.getAllValues().size();
            assertTrue(calls >= 1 && calls <= 4);

            var content = moderateResponse.content();
            assertTrue(content.flaggedText().equals("input1")
                    || content.flaggedText().equals("input"));
            assertTrue(content.flagged());

            var metadata = moderateResponse.metadata();
            assertEquals("xxx", metadata.get("detection"));
            assertEquals("Pii", metadata.get("detection_type"));
            assertEquals(5, metadata.get("end"));
            assertEquals(0, metadata.get("start"));
            assertEquals(0.3, metadata.get("score"));

            calls = detectionTextRequest.getValue().detectors().size();
            assertTrue(calls == 1 || calls == 2);
        });
    }

    @Test
    void should_return_not_flagged() {

        when(mockDetectionService.detect(detectionTextRequest.capture()))
                .thenAnswer(invocation -> new DetectionResponse<>(List.of()))
                .thenAnswer(invocation -> new DetectionResponse<>(List.of()))
                .thenAnswer(invocation -> new DetectionResponse<>(List.of()))
                .thenAnswer(invocation -> new DetectionResponse<>(List.of()));

        withDetectionServiceMock(() -> {
            ModerationModel model = WatsonxModerationModel.builder()
                    .baseUrl("https://test.com")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
                    .build();

            var moderateResponse = model.moderate(List.of(
                    SystemMessage.from("systemMessage"),
                    UserMessage.from("userMessage"),
                    AiMessage.from("aiMessage"),
                    ToolExecutionResultMessage.from("id", "toolName", "toolExecutionResult")));

            verify(mockDetectionService, times(4)).detect(any());
            assertEquals(4, detectionTextRequest.getAllValues().size());

            var content = moderateResponse.content();
            assertNull(content.flaggedText());
            assertFalse(content.flagged());
        });
    }

    @Test
    void should_throw_langchain4j_exception() {

        when(mockDetectionService.detect(detectionTextRequest.capture()))
                .thenThrow(new WatsonxException("errormessage", 400, null));

        withDetectionServiceMock(() -> {
            ModerationModel model = WatsonxModerationModel.builder()
                    .baseUrl("https://test.com")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
                    .build();

            var ex = assertThrows(
                    LangChain4jException.class,
                    () -> model.moderate(List.of(
                            SystemMessage.from("systemMessage"),
                            UserMessage.from("userMessage"),
                            AiMessage.from("aiMessage"),
                            ToolExecutionResultMessage.from("id", "toolName", "toolExecutionResult"))));

            assertEquals("errormessage", ex.getMessage());
        });
    }

    @Test
    void should_throw_runtime_exception() {

        when(mockDetectionService.detect(detectionTextRequest.capture()))
                .thenThrow(new NullPointerException("errormessage"));

        withDetectionServiceMock(() -> {
            ModerationModel model = WatsonxModerationModel.builder()
                    .baseUrl("https://test.com")
                    .apiKey("api-key-test")
                    .projectId("project-id")
                    .detectors(Pii.ofDefaults(), GraniteGuardian.ofDefaults())
                    .build();

            var ex = assertThrows(
                    RuntimeException.class,
                    () -> model.moderate(List.of(
                            SystemMessage.from("systemMessage"),
                            UserMessage.from("userMessage"),
                            AiMessage.from("aiMessage"),
                            ToolExecutionResultMessage.from("id", "toolName", "toolExecutionResult"))));

            assertEquals("errormessage", ex.getCause().getMessage());
        });
    }

    private void withDetectionServiceMock(Runnable action) {
        try (MockedStatic<DetectionService> mockedStatic = mockStatic(DetectionService.class)) {
            mockedStatic.when(DetectionService::builder).thenReturn(mockDetectionServiceBuilder);
            action.run();
        }
    }
}
