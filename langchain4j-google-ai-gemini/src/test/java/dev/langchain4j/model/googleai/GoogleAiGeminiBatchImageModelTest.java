package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_FAILED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_PENDING;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_RUNNING;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_SUCCEEDED;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.BATCH_GENERATE_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse.InlinedResponseWrapper;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchError;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchFileRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchIncomplete;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchList;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchName;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchSuccess;
import dev.langchain4j.model.googleai.BatchRequestResponse.ListOperationsResponse;
import dev.langchain4j.model.googleai.BatchRequestResponse.Operation;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart;
import dev.langchain4j.model.googleai.GeminiContent.GeminiPart.GeminiBlob;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate;
import dev.langchain4j.model.googleai.GeminiGenerateContentResponse.GeminiCandidate.GeminiFinishReason;
import dev.langchain4j.model.googleai.GoogleAiGeminiBatchImageModel.ImageGenerationRequest;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriters;
import dev.langchain4j.model.output.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiBatchImageModelTest {

    private static final String MODEL_NAME = "gemini-2.5-flash-image";
    private static final String API_KEY = "test-api-key";
    private static final String TEST_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk";
    private static final String TEST_MIME_TYPE = "image/png";

    @Mock
    private GeminiService mockGeminiService;

    @Captor
    private ArgumentCaptor<BatchCreateRequest<GeminiGenerateContentRequest>> batchRequestCaptor;

    private GoogleAiGeminiBatchImageModel subject;

    @BeforeEach
    void setUp() {
        subject = createSubject();
    }

    @Nested
    class CreateBatchInline {

        @Test
        void should_create_batch_with_valid_requests() {
            // given
            var displayName = "Test Image Batch";
            var priority = 1L;
            var requests = List.of(
                    new ImageGenerationRequest("A serene mountain landscape"),
                    new ImageGenerationRequest("A futuristic cityscape"),
                    new ImageGenerationRequest("A cute cartoon cat"));
            var expectedOperation = createPendingOperation("batches/test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isEqualTo(priority);
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(3);
        }

        @Test
        void should_create_batch_with_null_priority_defaulting_to_zero() {
            // given
            var displayName = "Test Batch";
            var requests = List.of(new ImageGenerationRequest("A simple red circle"));
            var expectedOperation = createPendingOperation("batches/test-456", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, null, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-456"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().priority()).isZero();
        }

        @Test
        void should_create_batch_with_single_request() {
            // given
            var displayName = "Single Request Batch";
            var priority = 5L;
            var requests = List.of(new ImageGenerationRequest("A minimalist logo design"));
            var expectedOperation = createPendingOperation("batches/test-789", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-789"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(1);
        }

        @Test
        void should_create_batch_with_negative_priority() {
            // given
            var displayName = "Low Priority Batch";
            var priority = -10L;
            var requests = List.of(new ImageGenerationRequest("A product mockup"));
            var expectedOperation = createPendingOperation("batches/test-negative", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, requests);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/test-negative"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().priority()).isEqualTo(-10L);
        }

        @Test
        void should_include_image_config_when_aspect_ratio_is_set() {
            // given
            var subjectWithConfig = new GoogleAiGeminiBatchImageModel(
                    GoogleAiGeminiBatchImageModel.builder()
                            .apiKey(API_KEY)
                            .modelName(MODEL_NAME)
                            .aspectRatio("16:9")
                            .imageSize("2K"),
                    mockGeminiService);

            var displayName = "Image Config Test";
            var requests = List.of(new ImageGenerationRequest("A landscape image"));
            var expectedOperation = createPendingOperation("batches/test-config", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            subjectWithConfig.createBatchInline(displayName, 1L, requests);

            // then
            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            var inlinedRequest =
                    capturedRequest.batch().inputConfig().requests().requests().get(0);
            assertThat(inlinedRequest.request().generationConfig().imageConfig())
                    .isNotNull();
            assertThat(inlinedRequest.request().generationConfig().imageConfig().aspectRatio())
                    .isEqualTo("16:9");
            assertThat(inlinedRequest.request().generationConfig().imageConfig().imageSize())
                    .isEqualTo("2K");
        }

        @Test
        void should_set_response_modalities_to_image() {
            // given
            var displayName = "Modalities Test";
            var requests = List.of(new ImageGenerationRequest("Test image"));
            var expectedOperation = createPendingOperation("batches/test-modalities", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiGenerateContentRequest>>any(),
                            eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            subject.createBatchInline(displayName, 1L, requests);

            // then
            verify(mockGeminiService)
                    .batchCreate(eq(MODEL_NAME), batchRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            var inlinedRequest =
                    capturedRequest.batch().inputConfig().requests().requests().get(0);
            assertThat(inlinedRequest.request().generationConfig().responseModalities())
                    .containsExactly(GeminiResponseModality.IMAGE);
        }
    }

    @Nested
    class CreateBatchFromFile {

        @Captor
        private ArgumentCaptor<BatchCreateFileRequest> batchFileRequestCaptor;

        @Mock
        private GeminiFile mockGeminiFile;

        @Test
        void should_create_batch_from_file_with_valid_parameters() {
            // given
            String displayName = "Image Batch from File";
            when(mockGeminiFile.name()).thenReturn("files/test-file-123");
            var expectedOperation = createPendingOperation("batches/image-file-test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(BATCH_GENERATE_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(
                            new BatchIncomplete<>(new BatchName("batches/image-file-test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), batchFileRequestCaptor.capture(), eq(BATCH_GENERATE_CONTENT));

            var capturedRequest = batchFileRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().inputConfig().fileName()).isEqualTo("files/test-file-123");
        }

        @Test
        void should_throw_exception_when_creating_batch_from_file_fails() {
            // given
            String displayName = "Batch from File";
            when(mockGeminiFile.name()).thenReturn("files/test-file-error");
            when(mockGeminiService.<GeminiGenerateContentRequest, GeminiGenerateContentResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(BATCH_GENERATE_CONTENT)))
                    .thenThrow(new RuntimeException("Error creating batch from file"));

            // when & then
            assertThatThrownBy(() -> subject.createBatchFromFile(displayName, mockGeminiFile))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error creating batch from file");
        }
    }

    @Nested
    class WriteBatchToFile {

        private Path tempFile;

        @BeforeEach
        void setUp() throws IOException {
            tempFile = Files.createTempFile("testImageBatchFile", ".jsonl");
        }

        @AfterEach
        void tearDown() throws IOException {
            Files.deleteIfExists(tempFile);
        }

        @Test
        void should_write_single_request_to_file() throws Exception {
            // given
            var request = new BatchFileRequest<>("key-1", new ImageGenerationRequest("A sunset over mountains"));
            var requests = List.of(request);

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiGenerateContentRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).hasSize(1);
            assertThat(writtenRequests.get(0).contents().get(0).parts().get(0).text())
                    .isEqualTo("A sunset over mountains");
        }

        @Test
        void should_write_multiple_requests_to_file() throws Exception {
            // given
            var requests = List.of(
                    new BatchFileRequest<>("key-1", new ImageGenerationRequest("First image prompt")),
                    new BatchFileRequest<>("key-2", new ImageGenerationRequest("Second image prompt")),
                    new BatchFileRequest<>("key-3", new ImageGenerationRequest("Third image prompt")));

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiGenerateContentRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).hasSize(3);
            assertThat(writtenRequests.get(0).contents().get(0).parts().get(0).text())
                    .isEqualTo("First image prompt");
            assertThat(writtenRequests.get(1).contents().get(0).parts().get(0).text())
                    .isEqualTo("Second image prompt");
            assertThat(writtenRequests.get(2).contents().get(0).parts().get(0).text())
                    .isEqualTo("Third image prompt");
        }

        @Test
        void should_handle_empty_requests_list() throws Exception {
            // given
            List<BatchFileRequest<ImageGenerationRequest>> requests = List.of();

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiGenerateContentRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).isEmpty();
        }

        private List<GeminiGenerateContentRequest> readRequestsFromFile(Path file) throws IOException {
            List<GeminiGenerateContentRequest> requests = new ArrayList<>();
            ObjectMapper testMapper = new ObjectMapper();

            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    BatchFileRequest<GeminiGenerateContentRequest> batchRequest =
                            testMapper.readValue(line, new TypeReference<>() {});
                    requests.add(batchRequest.request());
                }
            }
            return requests;
        }
    }

    @Nested
    class RetrieveBatchResults {

        @Test
        void should_return_pending_when_batch_is_still_processing() {
            // given
            var batchName = new BatchName("batches/test-pending");
            var pendingOperation = createPendingOperation("batches/test-pending", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(pendingOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(batchName, BATCH_STATE_PENDING));
        }

        @Test
        void should_return_pending_when_batch_is_running() {
            // given
            var batchName = new BatchName("batches/test-running");
            var runningOperation = createPendingOperation("batches/test-running", BATCH_STATE_RUNNING);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(runningOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(batchName, BATCH_STATE_RUNNING));
        }

        @Test
        void should_return_success_when_batch_processing_is_completed() {
            // given
            var batchName = new BatchName("batches/test-success");
            var imageResponses = List.of(
                    createImageResponse(TEST_IMAGE_BASE64, TEST_MIME_TYPE),
                    createImageResponse("secondImageData", TEST_MIME_TYPE));
            var successOperation = createSuccessOperation("batches/test-success", imageResponses);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<Response<Image>>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).hasSize(2);
            assertThat(successResult.responses().get(0).content().base64Data()).isEqualTo(TEST_IMAGE_BASE64);
            assertThat(successResult.responses().get(1).content().base64Data()).isEqualTo("secondImageData");
        }

        @Test
        void should_return_success_with_errors_when_batch_processing_has_individual_failures() {
            // given
            var batchName = new BatchName("batches/test-partial-success");
            var imageResponses = List.of(
                    createImageResponse(TEST_IMAGE_BASE64, TEST_MIME_TYPE),
                    createImageResponse("secondImageData", TEST_MIME_TYPE));
            var error = new BatchRequestResponse.Operation.Status(
                    4, "Deadline expired before operation could complete.", null);
            var successOperation =
                    createSuccessOperationWithError("batches/test-partial-success", imageResponses, error);
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<Response<Image>>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).hasSize(2);
            assertThat(successResult.responses().get(0).content().base64Data()).isEqualTo(TEST_IMAGE_BASE64);
            assertThat(successResult.responses().get(1).content().base64Data()).isEqualTo("secondImageData");

            assertThat(successResult.errors()).hasSize(1);
            assertThat(successResult.errors().get(0).code()).isEqualTo(4);
            assertThat(successResult.errors().get(0).message())
                    .isEqualTo("Deadline expired before operation could complete.");
        }

        @Test
        void should_return_success_with_empty_responses_when_response_is_null() {
            // given
            var batchName = new BatchName("batches/test-empty");
            var successOperation = createSuccessOperationWithNullResponse("batches/test-empty");
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<Response<Image>>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEmpty();
        }

        @Test
        void should_return_error_when_batch_processing_is_cancelled() {
            // given
            var batchName = new BatchName("batches/test-cancelled");
            var cancelledOperation = createCancelledOperation("batches/test-cancelled", "Batch was cancelled");
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(cancelledOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(
                            new BatchError<>(batchName, 13, "Batch was cancelled", BATCH_STATE_CANCELLED, List.of()));
        }

        @Test
        void should_return_error_when_batch_processing_fails() {
            // given
            var batchName = new BatchName("batches/test-error");
            var errorOperation = createErrorOperation("batches/test-error", 500, "Internal Server Error", List.of());
            when(mockGeminiService.<GeminiGenerateContentResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(
                            new BatchError<>(batchName, 500, "Internal Server Error", BATCH_STATE_FAILED, List.of()));
        }
    }

    @Nested
    class BatchImageSerialization {

        private static final String IMAGE_PENDING_RESPONSE =
                """
                {
                  "name": "batches/image-test-123",
                  "metadata": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatch",
                    "model": "models/gemini-2.5-flash-preview-image-generation",
                    "displayName": "images-batch",
                    "state": "BATCH_STATE_PENDING",
                    "name": "batches/image-test-123"
                  }
                }
                """;

        private static final String IMAGE_SUCCEEDED_RESPONSE =
                """
                {
                  "name": "batches/image-test-123",
                  "metadata": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatch",
                    "model": "models/gemini-2.5-flash-preview-image-generation",
                    "displayName": "images-batch",
                    "state": "BATCH_STATE_SUCCEEDED",
                    "name": "batches/image-test-123"
                  },
                  "done": true,
                  "response": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatchOutput",
                    "inlinedResponses": {
                      "inlinedResponses": [
                        {
                          "response": {
                            "candidates": [
                              {
                                "content": {
                                  "parts": [
                                    {
                                      "inlineData": {
                                        "mimeType": "image/png",
                                        "data": "aW1hZ2UxYmFzZTY0ZGF0YQ=="
                                      }
                                    }
                                  ],
                                  "role": "model"
                                },
                                "finishReason": "STOP",
                                "index": 0
                              }
                            ],
                            "usageMetadata": {
                              "promptTokenCount": 10,
                              "candidatesTokenCount": 256,
                              "totalTokenCount": 266
                            },
                            "modelVersion": "gemini-2.5-flash-preview-image-generation"
                          }
                        },
                        {
                          "response": {
                            "candidates": [
                              {
                                "content": {
                                  "parts": [
                                    {
                                      "inlineData": {
                                        "mimeType": "image/jpeg",
                                        "data": "aW1hZ2UyYmFzZTY0ZGF0YQ=="
                                      }
                                    }
                                  ],
                                  "role": "model"
                                },
                                "finishReason": "STOP",
                                "index": 0
                              }
                            ],
                            "usageMetadata": {
                              "promptTokenCount": 12,
                              "candidatesTokenCount": 300,
                              "totalTokenCount": 312
                            },
                            "modelVersion": "gemini-2.5-flash-preview-image-generation"
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        @Test
        void should_deserialize_pending_image_batch_response() {
            // given
            var mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(IMAGE_PENDING_RESPONSE)
                    .build());
            var subject = GoogleAiGeminiBatchImageModel.builder()
                    .apiKey(API_KEY)
                    .modelName("gemini-2.5-flash-preview-image-generation")
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();

            var requests = List.of(
                    new GoogleAiGeminiBatchImageModel.ImageGenerationRequest("A sunset over mountains"),
                    new GoogleAiGeminiBatchImageModel.ImageGenerationRequest("A cat wearing a hat"));

            // when
            var result = subject.createBatchInline("images-batch", 0L, requests);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);
            var incomplete = (BatchIncomplete<?>) result;
            assertThat(incomplete.batchName().value()).isEqualTo("batches/image-test-123");
            assertThat(incomplete.state()).isEqualTo(BATCH_STATE_PENDING);
        }

        @Test
        void should_deserialize_succeeded_image_batch_response() {
            // given
            var mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(IMAGE_SUCCEEDED_RESPONSE)
                    .build());
            var subject = GoogleAiGeminiBatchImageModel.builder()
                    .apiKey(API_KEY)
                    .modelName("gemini-2.5-flash-preview-image-generation")
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();

            var batchName = new BatchName("batches/image-test-123");

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var success = (BatchSuccess<Response<Image>>) result;
            assertThat(success.batchName().value()).isEqualTo("batches/image-test-123");

            var results = success.responses();
            assertThat(results).hasSize(2);

            assertThat(results.get(0).content().base64Data()).isEqualTo("aW1hZ2UxYmFzZTY0ZGF0YQ==");
            assertThat(results.get(0).content().mimeType()).isEqualTo("image/png");

            assertThat(results.get(1).content().base64Data()).isEqualTo("aW1hZ2UyYmFzZTY0ZGF0YQ==");
            assertThat(results.get(1).content().mimeType()).isEqualTo("image/jpeg");
        }

        @Test
        void should_deserialize_image_batch_response_with_error() {
            // given
            String IMAGE_ERROR_RESPONSE =
                    """
            {
              "name": "batches/image-test-123",
              "metadata": {
                "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatch",
                "model": "models/gemini-2.5-flash-preview-image-generation",
                "displayName": "images-batch",
                "state": "BATCH_STATE_SUCCEEDED",
                "name": "batches/image-test-123"
              },
              "done": true,
              "response": {
                "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatchOutput",
                "inlinedResponses": {
                  "inlinedResponses": [
                    {
                      "response": {
                        "candidates": [
                          {
                            "content": {
                              "parts": [
                                {
                                  "inlineData": {
                                    "mimeType": "image/png",
                                    "data": "aW1hZ2UxYmFzZTY0ZGF0YQ=="
                                  }
                                }
                              ],
                              "role": "model"
                            },
                            "finishReason": "STOP",
                            "index": 0
                          }
                        ],
                        "usageMetadata": {
                          "promptTokenCount": 10,
                          "candidatesTokenCount": 256,
                          "totalTokenCount": 266
                        },
                        "modelVersion": "gemini-2.5-flash-preview-image-generation"
                      }
                    },
                    {
                      "error": {
                        "code": 4,
                        "message": "Deadline expired before operation could complete."
                      }
                    },
                    {
                      "response": {
                        "candidates": [
                          {
                            "content": {
                              "parts": [
                                {
                                  "inlineData": {
                                    "mimeType": "image/jpeg",
                                    "data": "aW1hZ2UyYmFzZTY0ZGF0YQ=="
                                  }
                                }
                              ],
                              "role": "model"
                            },
                            "finishReason": "STOP",
                            "index": 0
                          }
                        ],
                        "usageMetadata": {
                          "promptTokenCount": 12,
                          "candidatesTokenCount": 300,
                          "totalTokenCount": 312
                        },
                        "modelVersion": "gemini-2.5-flash-preview-image-generation"
                      }
                    }
                  ]
                }
              }
            }
            """;

            var mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(IMAGE_ERROR_RESPONSE)
                    .build());
            var subject = GoogleAiGeminiBatchImageModel.builder()
                    .apiKey(API_KEY)
                    .modelName("gemini-2.5-flash-preview-image-generation")
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();

            var batchName = new BatchName("batches/image-test-123");

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var success = (BatchSuccess<Response<Image>>) result;
            assertThat(success.batchName().value()).isEqualTo("batches/image-test-123");

            var results = success.responses();
            assertThat(results).hasSize(2);

            assertThat(results.get(0).content().base64Data()).isEqualTo("aW1hZ2UxYmFzZTY0ZGF0YQ==");
            assertThat(results.get(0).content().mimeType()).isEqualTo("image/png");

            assertThat(results.get(1).content().base64Data()).isEqualTo("aW1hZ2UyYmFzZTY0ZGF0YQ==");
            assertThat(results.get(1).content().mimeType()).isEqualTo("image/jpeg");

            assertThat(success.errors()).hasSize(1);
            assertThat(success.errors().get(0).code()).isEqualTo(4);
            assertThat(success.errors().get(0).message())
                    .isEqualTo("Deadline expired before operation could complete.");
        }
    }

    @Nested
    class CancelBatchJob {

        @Test
        void should_cancel_pending_batch() {
            // given
            var batchName = new BatchName("batches/test-pending-cancel");

            // when
            subject.cancelBatchJob(batchName);

            // then
            verify(mockGeminiService).batchCancelBatch("batches/test-pending-cancel");
        }

        @ParameterizedTest
        @CsvSource({"batches/test-cannot-cancel, Batch cannot be cancelled", "batches/non-existent, Batch not found"})
        void should_throw_exception_when_batch_cancellation_fails(String batchNameValue, String errorMessage) {
            // given
            var batchName = new BatchName(batchNameValue);
            when(mockGeminiService.batchCancelBatch(batchName.value())).thenThrow(new RuntimeException(errorMessage));

            // when & then
            assertThatThrownBy(() -> subject.cancelBatchJob(batchName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);
        }
    }

    @Nested
    class DeleteBatchJob {

        @Test
        void should_delete_batch() {
            // given
            var batchName = new BatchName("batches/test-completed-delete");

            // when
            subject.deleteBatchJob(batchName);

            // then
            verify(mockGeminiService).batchDeleteBatch("batches/test-completed-delete");
        }

        @Test
        void should_throw_exception_when_batch_deletion_fails() {
            // given
            var batchName = new BatchName("batches/non-existent");
            when(mockGeminiService.batchDeleteBatch(batchName.value()))
                    .thenThrow(new RuntimeException("Batch not found"));

            // when & then
            assertThatThrownBy(() -> subject.deleteBatchJob(batchName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Batch not found");
        }
    }

    @Nested
    class ListBatchJobs {

        @Test
        void should_list_batch_jobs_with_default_parameters() {
            // given
            var operation1 = createMockOperation("batches/batch-1", BATCH_STATE_SUCCEEDED);
            var operation2 = createMockOperation("batches/batch-2", BATCH_STATE_RUNNING);
            var listResponse = new ListOperationsResponse<>(List.of(operation1, operation2), null);

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(null, null))
                    .thenReturn(listResponse);

            // when
            BatchList<Response<Image>> result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).hasSize(2);
            verify(mockGeminiService).batchListBatches(null, null);
        }

        @Test
        void should_list_batch_jobs_with_pagination() {
            // given
            Integer pageSize = 10;
            String pageToken = "next-token";
            var operation = createMockOperation("batches/batch-1", BATCH_STATE_SUCCEEDED);
            var listResponse = new ListOperationsResponse<>(List.of(operation), "another-token");

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(pageSize, pageToken))
                    .thenReturn(listResponse);

            // when
            BatchList<Response<Image>> result = subject.listBatchJobs(pageSize, pageToken);

            // then
            assertThat(result.responses()).hasSize(1);
            assertThat(result.pageToken()).isEqualTo("another-token");
            verify(mockGeminiService).batchListBatches(pageSize, pageToken);
        }

        @Test
        void should_return_empty_list_when_no_batch_jobs_exist() {
            // given
            var listResponse = new ListOperationsResponse<GeminiGenerateContentResponse>(List.of(), null);

            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(null, null))
                    .thenReturn(listResponse);

            // when
            BatchList<Response<Image>> result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).isEmpty();
        }

        private Operation<GeminiGenerateContentResponse> createMockOperation(String name, BatchJobState state) {
            return new Operation<>(name, Map.of("state", state), false, null, null);
        }
    }

    @Nested
    class ImageGenerationRequestValidation {

        @Test
        void should_throw_exception_when_prompt_is_null() {
            assertThatThrownBy(() -> new ImageGenerationRequest(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("prompt");
        }

        @Test
        void should_throw_exception_when_prompt_is_blank() {
            assertThatThrownBy(() -> new ImageGenerationRequest(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("prompt");
        }

        @Test
        void should_create_request_with_valid_prompt() {
            var request = new ImageGenerationRequest("A beautiful landscape");
            assertThat(request.prompt()).isEqualTo("A beautiful landscape");
        }
    }

    @Nested
    class BuilderValidation {

        @Test
        void should_use_default_model_name_when_not_specified() {
            // This test verifies the default model is used
            var model = GoogleAiGeminiBatchImageModel.builder().apiKey(API_KEY).build();

            // The model should be created successfully with default model name
            assertThat(model).isNotNull();
        }
    }

    private GoogleAiGeminiBatchImageModel createSubject() {
        return new GoogleAiGeminiBatchImageModel(
                GoogleAiGeminiBatchImageModel.builder().apiKey(API_KEY).modelName(MODEL_NAME), mockGeminiService);
    }

    private static Operation<GeminiGenerateContentResponse> createPendingOperation(
            String operationName, BatchJobState state) {
        return new Operation<>(operationName, Map.of("state", state.name()), false, null, null);
    }

    private static GeminiGenerateContentResponse createImageResponse(String base64Data, String mimeType) {
        var part = GeminiPart.builder()
                .inlineData(new GeminiBlob(mimeType, base64Data))
                .build();
        var content = new GeminiContent(List.of(part), "model");
        var candidate = new GeminiCandidate(content, GeminiFinishReason.STOP, null);

        return new GeminiGenerateContentResponse("response-id", MODEL_NAME, List.of(candidate), null, null);
    }

    private static Operation<GeminiGenerateContentResponse> createSuccessOperation(
            String operationName, List<GeminiGenerateContentResponse> imageResponses) {
        var inlinedResponses = imageResponses.stream()
                .map(response -> new InlinedResponseWrapper<>(response, null))
                .toList();

        var response = new BatchCreateResponse<>(
                "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatchOutput",
                new BatchCreateResponse.InlinedResponses<>(inlinedResponses));

        return new Operation<>(operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, response);
    }

    private static Operation<GeminiGenerateContentResponse> createSuccessOperationWithError(
            String operationName,
            List<GeminiGenerateContentResponse> imageResponses,
            BatchRequestResponse.Operation.Status error) {
        List<InlinedResponseWrapper<GeminiGenerateContentResponse>> inlinedResponses = new ArrayList<>();

        // Add first successful response
        if (!imageResponses.isEmpty()) {
            inlinedResponses.add(new InlinedResponseWrapper<>(imageResponses.get(0), null));
        }

        // Add error
        inlinedResponses.add(new InlinedResponseWrapper<>(null, error));

        // Add remaining successful responses
        for (int i = 1; i < imageResponses.size(); i++) {
            inlinedResponses.add(new InlinedResponseWrapper<>(imageResponses.get(i), null));
        }

        var response = new BatchCreateResponse<>(
                "type.googleapis.com/google.ai.generativelanguage.v1main.GenerateContentBatchOutput",
                new BatchCreateResponse.InlinedResponses<>(inlinedResponses));

        return new Operation<>(operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, response);
    }

    private static Operation<GeminiGenerateContentResponse> createSuccessOperationWithNullResponse(
            String operationName) {
        return new Operation<>(operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, null);
    }

    private static Operation<GeminiGenerateContentResponse> createErrorOperation(
            String operationName, int errorCode, String errorMessage, List<Map<String, Object>> errorDetails) {
        var errorStatus = new Operation.Status(errorCode, errorMessage, errorDetails);
        return new Operation<>(operationName, Map.of("state", BATCH_STATE_FAILED.name()), true, errorStatus, null);
    }

    private static Operation<GeminiGenerateContentResponse> createCancelledOperation(
            String operationName, String errorMessage) {
        var errorStatus = new Operation.Status(13, errorMessage, List.of());
        return new Operation<>(operationName, Map.of("state", BATCH_STATE_CANCELLED.name()), true, errorStatus, null);
    }
}
