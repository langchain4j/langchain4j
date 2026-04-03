package dev.langchain4j.model.googleai;

import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_CANCELLED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_FAILED;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_PENDING;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_RUNNING;
import static dev.langchain4j.model.googleai.BatchRequestResponse.BatchJobState.BATCH_STATE_SUCCEEDED;
import static dev.langchain4j.model.googleai.GeminiService.BatchOperationType.ASYNC_BATCH_EMBED_CONTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
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
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingRequest;
import dev.langchain4j.model.googleai.GeminiEmbeddingRequestResponse.GeminiEmbeddingResponse;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel.TaskType;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriters;
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
class GoogleAiGeminiBatchEmbeddingModelTest {
    public static final String MODEL_NAME = "gemini-embedding-001";
    public static final String API_KEY = "test-api-key";

    @Mock
    private GeminiService mockGeminiService;

    private GoogleAiGeminiBatchEmbeddingModel subject;

    @BeforeEach
    void setUp() {
        subject = createSubject();
    }

    @Nested
    class CreateBatchInline {
        @Captor
        private ArgumentCaptor<BatchCreateRequest<GeminiEmbeddingRequest>> batchRequestCaptor;

        @Test
        void should_create_batch_with_valid_segments() {
            // given
            var displayName = "Test Embedding Batch";
            var priority = 1L;
            var segments =
                    List.of(TextSegment.from("What is machine learning?"), TextSegment.from("Explain neural networks"));
            var expectedOperation = createPendingOperation("batches/embed-test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, segments);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/embed-test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isEqualTo(priority);
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(2);
        }

        @Test
        void should_create_batch_with_null_priority_defaulting_to_zero() {
            // given
            var displayName = "Test Batch";
            var segments = List.of(TextSegment.from("Sample text for embedding"));
            var expectedOperation = createPendingOperation("batches/embed-test-456", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, null, segments);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/embed-test-456"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isZero();
        }

        @Test
        void should_create_batch_with_single_segment() {
            // given
            var displayName = "Single Segment Batch";
            var priority = 5L;
            var segments = List.of(TextSegment.from("Single text segment"));
            var expectedOperation = createPendingOperation("batches/embed-test-789", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, segments);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/embed-test-789"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(1);
        }

        @Test
        void should_create_batch_with_negative_priority() {
            // given
            var displayName = "Low Priority Batch";
            var priority = -10L;
            var segments = List.of(TextSegment.from("Low priority text"));
            var expectedOperation = createPendingOperation("batches/embed-test-negative", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, segments);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(
                            new BatchIncomplete<>(new BatchName("batches/embed-test-negative"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().priority()).isEqualTo(-10L);
        }

        @Test
        void should_wrap_segments_in_inlined_request_with_empty_metadata() {
            // given
            var displayName = "Metadata Test";
            var priority = 1L;
            var segments = List.of(TextSegment.from("Test segment"));
            var expectedOperation = createPendingOperation("batches/embed-test-metadata", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, segments);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            var inlinedRequests =
                    capturedRequest.batch().inputConfig().requests().requests();
            assertThat(inlinedRequests).hasSize(1);
            assertThat(inlinedRequests.get(0).metadata()).isEmpty();
        }

        @Test
        void should_create_batch_with_multiple_segments() {
            // given
            var displayName = "Multiple Segments Batch";
            var priority = 2L;
            var segments = List.of(
                    TextSegment.from("Segment 1"),
                    TextSegment.from("Segment 2"),
                    TextSegment.from("Segment 3"),
                    TextSegment.from("Segment 4"),
                    TextSegment.from("Segment 5"));
            var expectedOperation = createPendingOperation("batches/embed-test-multiple", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, segments);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(
                            new BatchIncomplete<>(new BatchName("batches/embed-test-multiple"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(5);
        }

        @Test
        void should_create_batch_with_segments_containing_metadata() {
            // given
            var displayName = "Segments With Metadata";
            var priority = 1L;
            var segment1 = TextSegment.from("Document about AI", Metadata.from("title", "AI Introduction"));
            var segment2 = TextSegment.from("Document about ML", Metadata.from("title", "ML Basics"));
            var segments = List.of(segment1, segment2);
            var expectedOperation = createPendingOperation("batches/embed-test-with-meta", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, segments);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().inputConfig().requests().requests())
                    .hasSize(2);
        }

        @Test
        void should_use_correct_model_name() {
            // given
            var displayName = "Model Name Test";
            var priority = 1L;
            var segments = List.of(TextSegment.from("Test text"));
            var expectedOperation = createPendingOperation("batches/embed-test-model", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, segments);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(),
                            ArgumentMatchers.<BatchCreateRequest<GeminiEmbeddingRequest>>any(),
                            eq(ASYNC_BATCH_EMBED_CONTENT));
        }
    }

    @Nested
    class CreateBatchFromFile {
        @Captor
        private ArgumentCaptor<BatchCreateFileRequest> batchRequestCaptor;

        @Mock
        private GeminiFile mockGeminiFile;

        @Test
        void should_create_batch_from_file_with_valid_parameters() {
            // given
            String displayName = "Batch from File";
            when(mockGeminiFile.name()).thenReturn("files/test-file-123");
            var expectedOperation = createPendingOperation("batches/embed-file-test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(
                            new BatchIncomplete<>(new BatchName("batches/embed-file-test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().inputConfig().fileName()).isEqualTo("files/test-file-123");
        }

        @Test
        void should_create_batch_from_file_with_null_priority() {
            // given
            String displayName = "Batch from File with Null Priority";
            when(mockGeminiFile.name()).thenReturn("files/test-file-456");
            var expectedOperation = createPendingOperation("batches/embed-file-test-456", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(
                            new BatchIncomplete<>(new BatchName("batches/embed-file-test-456"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().inputConfig().fileName()).isEqualTo("files/test-file-456");
        }

        @Test
        void should_throw_exception_when_creating_batch_from_file_fails() {
            // given
            String displayName = "Batch from File";
            when(mockGeminiFile.name()).thenReturn("files/test-file-error");
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenThrow(new RuntimeException("Error creating batch from file"));

            // when & then
            assertThatThrownBy(() -> subject.createBatchFromFile(displayName, mockGeminiFile))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Error creating batch from file");
        }

        @Test
        void should_use_correct_model_name_when_creating_batch_from_file() {
            // given
            String displayName = "Model Name Test";
            Long priority = 1L;
            when(mockGeminiFile.name()).thenReturn("files/test-file-model");
            var expectedOperation = createPendingOperation("batches/embed-file-model", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            subject.createBatchFromFile(displayName, mockGeminiFile);

            // then
            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), any(BatchCreateFileRequest.class), eq(ASYNC_BATCH_EMBED_CONTENT));
        }
    }

    @Nested
    class WriteBatchToFile {
        private Path tempFile;

        @BeforeEach
        void setUp() throws IOException {
            tempFile = Files.createTempFile("testBatchFile", ".jsonl");
        }

        @AfterEach
        void tearDown() throws IOException {
            Files.deleteIfExists(tempFile);
        }

        @Test
        void should_write_single_request_to_file() throws Exception {
            // given
            var segment = TextSegment.from("Sample text");
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            var capturedRequest = writtenRequests.get(0);
            assertThat(capturedRequest.model()).isEqualTo("models/" + MODEL_NAME);
            assertThat(capturedRequest.content().parts()).hasSize(1);
            assertThat(capturedRequest.content().parts().get(0).text()).isEqualTo("Sample text");
        }

        @Test
        void should_write_multiple_requests_to_file() throws Exception {
            // given
            var segment1 = TextSegment.from("First text");
            var segment2 = TextSegment.from("Second text");
            var segment3 = TextSegment.from("Third text");
            var requests = List.of(
                    new BatchFileRequest<>("key-1", segment1),
                    new BatchFileRequest<>("key-2", segment2),
                    new BatchFileRequest<>("key-3", segment3));

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).hasSize(3);
            assertThat(writtenRequests.get(0).content().parts().get(0).text()).isEqualTo("First text");
            assertThat(writtenRequests.get(1).content().parts().get(0).text()).isEqualTo("Second text");
            assertThat(writtenRequests.get(2).content().parts().get(0).text()).isEqualTo("Third text");
        }

        @Test
        void should_write_request_with_metadata_to_file() throws Exception {
            // given
            var segment = TextSegment.from("Document text", Metadata.from("title", "Document Title"));
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            var capturedRequest = writtenRequests.get(0);
            assertThat(capturedRequest.title()).isEqualTo("Document Title");
            assertThat(capturedRequest.content().parts().get(0).text()).isEqualTo("Document text");
        }

        @Test
        void should_write_request_without_title_when_metadata_is_null() throws Exception {
            // given
            var segment = TextSegment.from("Text without metadata");
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            var capturedRequest = writtenRequests.get(0);
            assertThat(capturedRequest.title()).isNull();
            assertThat(capturedRequest.content().parts().get(0).text()).isEqualTo("Text without metadata");
        }

        @Test
        void should_write_request_without_title_when_title_metadata_is_missing() throws Exception {
            // given
            var segment = TextSegment.from("Text with other metadata", Metadata.from("author", "John Doe"));
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            var capturedRequest = writtenRequests.get(0);
            assertThat(capturedRequest.title()).isNull();
        }

        @Test
        void should_handle_empty_requests_list() throws Exception {
            // given
            List<BatchFileRequest<TextSegment>> requests = List.of();

            // when
            try (var writer = JsonLinesWriters.streaming(tempFile)) {
                subject.writeBatchToFile(writer, requests);
            }

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).isEmpty();
        }

        @Test
        void should_include_task_type_in_written_request() throws Exception {
            // given
            var segment = TextSegment.from("Sample text");
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            subject.writeBatchToFile(JsonLinesWriters.streaming(tempFile), requests);

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            var capturedRequest = writtenRequests.get(0);
            assertThat(capturedRequest.taskType()).isEqualTo(TaskType.RETRIEVAL_DOCUMENT);
        }

        @Test
        void should_include_output_dimensionality_when_set() throws Exception {
            // given
            var subjectWithDimensionality = new GoogleAiGeminiBatchEmbeddingModel(
                    GoogleAiGeminiBatchEmbeddingModel.builder()
                            .apiKey("apiKey")
                            .modelName(MODEL_NAME)
                            .taskType(TaskType.RETRIEVAL_DOCUMENT)
                            .outputDimensionality(256),
                    mockGeminiService);
            var segment = TextSegment.from("Sample text");
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            subjectWithDimensionality.writeBatchToFile(JsonLinesWriters.streaming(tempFile), requests);

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            var capturedRequest = writtenRequests.get(0);
            assertThat(capturedRequest.outputDimensionality()).isEqualTo(256);
        }

        @Test
        void should_write_requests_with_different_text_lengths() throws Exception {
            // given
            var shortSegment = TextSegment.from("Short");
            var mediumSegment = TextSegment.from("This is a medium length text segment for testing");
            var longSegment = TextSegment.from("This is a very long text segment that contains much more content "
                    + "and is designed to test how the writer handles larger text inputs "
                    + "with multiple sentences and various punctuation marks.");
            var requests = List.of(
                    new BatchFileRequest<>("key-1", shortSegment),
                    new BatchFileRequest<>("key-2", mediumSegment),
                    new BatchFileRequest<>("key-3", longSegment));

            // when
            subject.writeBatchToFile(JsonLinesWriters.streaming(tempFile), requests);

            // then
            List<GeminiEmbeddingRequest> writtenRequests = readRequestsFromFile(tempFile);
            assertThat(writtenRequests).hasSize(3);
            assertThat(writtenRequests.get(0).content().parts().get(0).text()).isEqualTo("Short");
            assertThat(writtenRequests.get(1).content().parts().get(0).text())
                    .isEqualTo("This is a medium length text segment for testing");
            assertThat(writtenRequests.get(2).content().parts().get(0).text()).contains("very long text segment");
        }

        private List<GeminiEmbeddingRequest> readRequestsFromFile(Path file) throws IOException {
            List<GeminiEmbeddingRequest> requests = new ArrayList<>();
            ObjectMapper testMapper = new ObjectMapper();

            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    BatchFileRequest<GeminiEmbeddingRequest> batchRequest =
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
        void should_throw_when_invalid_batch_name() {
            assertThatThrownBy(() -> new BatchName("embed-test-pending"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Batch name must start with 'batches/'");
        }

        @Test
        void should_return_pending_when_batch_is_still_processing() {
            // given
            var batchName = new BatchName("batches/embed-test-pending");
            var pendingOperation = createPendingOperation("batches/embed-test-pending", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
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
            var batchName = new BatchName("batches/embed-test-running");
            var runningOperation = createPendingOperation("batches/embed-test-running", BATCH_STATE_RUNNING);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
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
            var batchName = new BatchName("batches/embed-test-success");
            var embeddings =
                    List.of(Embedding.from(List.of(0.1f, 0.2f, 0.3f)), Embedding.from(List.of(0.4f, 0.5f, 0.6f)));
            var successOperation = createSuccessOperation("batches/embed-test-success", embeddings);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<Embedding>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEqualTo(embeddings);
        }

        @Test
        void should_return_success_with_errors_when_batch_processing_has_individual_failures() {
            // given
            var batchName = new BatchName("batches/embed-test-partial-success");
            var embeddings =
                    List.of(Embedding.from(List.of(0.1f, 0.2f, 0.3f)), Embedding.from(List.of(0.4f, 0.5f, 0.6f)));
            var error = new BatchRequestResponse.Operation.Status(
                    4, "Deadline expired before operation could complete.", null);
            var successOperation =
                    createSuccessOperationWithError("batches/embed-test-partial-success", embeddings, error);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<Embedding>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEqualTo(embeddings);

            assertThat(successResult.errors()).hasSize(1);
            assertThat(successResult.errors().get(0).code()).isEqualTo(4);
            assertThat(successResult.errors().get(0).message())
                    .isEqualTo("Deadline expired before operation could complete.");
        }

        @Test
        void should_return_success_with_empty_responses_when_response_is_null() {
            // given
            var batchName = new BatchName("batches/embed-test-empty");
            var successOperation = createSuccessOperationWithNullResponse("batches/embed-test-empty");
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<Embedding>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEmpty();
        }

        @Test
        void should_return_error_when_batch_processing_is_cancelled() {
            // given
            var batchName = new BatchName("batches/embed-test-error");
            var errorOperation = createCancelledOperation(
                    "batches/embed-test-error", "batches/embed-test-error failed without error");
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError<>(
                            batchName,
                            13,
                            "batches/embed-test-error failed without error",
                            BATCH_STATE_CANCELLED,
                            List.of()));
        }

        @Test
        void should_return_error_when_batch_processing_fails() {
            // given
            var batchName = new BatchName("batches/embed-test-error");
            var errorOperation = createErrorOperation("batches/embed-test-error", 404, "Not Found", List.of());
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError<>(batchName, 404, "Not Found", BATCH_STATE_FAILED, List.of()));
        }

        @Test
        void should_return_error_with_details_when_batch_processing_fails_with_details() {
            // given
            var batchName = new BatchName("batches/embed-test-error-details");
            List<Map<String, Object>> errorDetails = List.of(
                    Map.of("@type", "type.googleapis.com/google.rpc.ErrorInfo", "reason", "INVALID_ARGUMENT"),
                    Map.of("@type", "type.googleapis.com/google.rpc.BadRequest", "field", "model"));
            var errorOperation =
                    createErrorOperation("batches/embed-test-error-details", 400, "Bad Request", errorDetails);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(errorOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchError.class)
                    .isEqualTo(new BatchError<>(batchName, 400, "Bad Request", BATCH_STATE_FAILED, errorDetails));
        }

        @Test
        void should_return_pending_with_unspecified_state_when_metadata_is_null() {
            // given
            var batchName = new BatchName("batches/embed-test-no-metadata");
            var operation = new BatchRequestResponse.Operation<GeminiEmbeddingResponse>(
                    "batches/embed-test-no-metadata", null, false, null, null);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(operation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(batchName, BatchJobState.UNSPECIFIED));
        }

        @Test
        void should_return_pending_with_unspecified_state_when_state_is_missing_from_metadata() {
            // given
            var batchName = new BatchName("batches/embed-test-no-state");
            var operation = new BatchRequestResponse.Operation<GeminiEmbeddingResponse>(
                    "batches/embed-test-no-state", Map.of("createTime", "2025-10-23T09:26:30Z"), false, null, null);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(operation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(batchName, BatchJobState.UNSPECIFIED));
        }

        @Test
        void should_return_success_with_single_response() {
            // given
            var batchName = new BatchName("batches/embed-test-single");
            var embeddings = List.of(Embedding.from(List.of(0.1f, 0.2f, 0.3f)));
            var successOperation = createSuccessOperation("batches/embed-test-single", embeddings);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<Embedding>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEqualTo(embeddings);
        }

        @Test
        void should_return_success_with_multiple_responses() {
            // given
            var batchName = new BatchName("batches/embed-test-multiple");
            var embeddings = List.of(
                    Embedding.from(List.of(0.1f, 0.2f)),
                    Embedding.from(List.of(0.3f, 0.4f)),
                    Embedding.from(List.of(0.5f, 0.6f)),
                    Embedding.from(List.of(0.7f, 0.8f)),
                    Embedding.from(List.of(0.9f, 1.0f)));
            var successOperation = createSuccessOperation("batches/embed-test-multiple", embeddings);
            when(mockGeminiService.<GeminiEmbeddingResponse>batchRetrieveBatch(batchName.value()))
                    .thenReturn(successOperation);

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var successResult = (BatchSuccess<Embedding>) result;
            assertThat(successResult.batchName()).isEqualTo(batchName);
            assertThat(successResult.responses()).isEqualTo(embeddings);
        }
    }

    @Nested
    class CancelBatchJob {
        @ParameterizedTest
        @CsvSource({
            "batches/embed-test-cannot-cancel, Batch cannot be cancelled because it has already completed",
            "batches/embed-test-already-cancelled, Batch is already in CANCELLED state",
            "batches/embed-non-existent, Batch not found"
        })
        void should_throw_exception_when_batch_cancellation_fails(String batchNameValue, String errorMessage) {
            // given
            var batchName = new BatchName(batchNameValue);
            when(mockGeminiService.batchCancelBatch(batchName.value())).thenThrow(new RuntimeException(errorMessage));

            // when & then
            assertThatThrownBy(() -> subject.cancelBatchJob(batchName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);
        }

        @Test
        void should_cancel_pending_batch() {
            // given
            var batchName = new BatchName("batches/embed-test-pending-cancel");

            // when
            subject.cancelBatchJob(batchName);

            // then
            verify(mockGeminiService).batchCancelBatch("batches/embed-test-pending-cancel");
        }

        @Test
        void should_cancel_running_batch() {
            // given
            var batchName = new BatchName("batches/embed-test-running-cancel");

            // when
            subject.cancelBatchJob(batchName);

            // then
            verify(mockGeminiService).batchCancelBatch("batches/embed-test-running-cancel");
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

        @ParameterizedTest
        @CsvSource({
            "batches/test-cannot-delete, Batch cannot be deleted due to server error",
            "batches/non-existent, Batch not found",
            "batches/invalid-name, Invalid batch name format"
        })
        void should_throw_exception_when_batch_deletion_fails(String batchNameValue, String errorMessage) {
            // given
            var batchName = new BatchName(batchNameValue);
            when(mockGeminiService.batchDeleteBatch(batchName.value())).thenThrow(new RuntimeException(errorMessage));

            // when & then
            assertThatThrownBy(() -> subject.deleteBatchJob(batchName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);
        }
    }

    @Nested
    class ListBatchJobs {
        @Test
        void should_return_empty_list_when_none_available() {
            // given
            when(mockGeminiService.<GeminiGenerateContentResponse>batchListBatches(null, null))
                    .thenReturn(new ListOperationsResponse<>(null, null));

            // when
            var result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).isEmpty();
        }

        @Test
        void should_list_batch_jobs_with_default_parameters() {
            // given
            var operation1 = createMockEmbeddingOperation("batches/batch-1", BatchJobState.BATCH_STATE_SUCCEEDED);
            var operation2 = createMockEmbeddingOperation("batches/batch-2", BatchJobState.BATCH_STATE_RUNNING);
            var listResponse = new ListOperationsResponse<>(List.of(operation1, operation2), null);

            when(mockGeminiService.<GeminiEmbeddingResponse>batchListBatches(null, null))
                    .thenReturn(listResponse);

            // when
            BatchList<Embedding> result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).hasSize(2);
            verify(mockGeminiService).batchListBatches(null, null);
        }

        @Test
        void should_list_batch_jobs_with_page_size() {
            // given
            Integer pageSize = 10;
            var operation = createMockEmbeddingOperation("batches/batch-1", BatchJobState.BATCH_STATE_SUCCEEDED);
            var listResponse = new ListOperationsResponse<>(List.of(operation), "next-page-token");

            when(mockGeminiService.<GeminiEmbeddingResponse>batchListBatches(pageSize, null))
                    .thenReturn(listResponse);

            // when
            BatchList<Embedding> result = subject.listBatchJobs(pageSize, null);

            // then
            assertThat(result.responses()).hasSize(1);
            verify(mockGeminiService).batchListBatches(pageSize, null);
        }

        @Test
        void should_list_batch_jobs_with_page_token() {
            // given
            String pageToken = "token-123";
            var operation = createMockEmbeddingOperation("batches/batch-1", BatchJobState.BATCH_STATE_SUCCEEDED);
            var listResponse = new ListOperationsResponse<>(List.of(operation), null);

            when(mockGeminiService.<GeminiEmbeddingResponse>batchListBatches(null, pageToken))
                    .thenReturn(listResponse);

            // when
            BatchList<Embedding> result = subject.listBatchJobs(null, pageToken);

            // then
            assertThat(result.responses()).hasSize(1);
            verify(mockGeminiService).batchListBatches(null, pageToken);
        }

        @Test
        void should_list_batch_jobs_with_both_page_size_and_token() {
            // given
            Integer pageSize = 5;
            String pageToken = "token-456";
            var operation = createMockEmbeddingOperation("batches/batch-1", BatchJobState.BATCH_STATE_PENDING);
            var listResponse = new ListOperationsResponse<>(List.of(operation), "next-token");

            when(mockGeminiService.<GeminiEmbeddingResponse>batchListBatches(pageSize, pageToken))
                    .thenReturn(listResponse);

            // when
            BatchList<Embedding> result = subject.listBatchJobs(pageSize, pageToken);

            // then
            assertThat(result.responses()).hasSize(1);
            verify(mockGeminiService).batchListBatches(pageSize, pageToken);
        }

        @Test
        void should_return_empty_list_when_no_batch_jobs_exist() {
            // given
            var listResponse = new ListOperationsResponse<GeminiEmbeddingResponse>(List.of(), null);

            when(mockGeminiService.<GeminiEmbeddingResponse>batchListBatches(null, null))
                    .thenReturn(listResponse);

            // when
            BatchList<Embedding> result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).isEmpty();
            verify(mockGeminiService).batchListBatches(null, null);
        }

        @Test
        void should_handle_multiple_batch_jobs_with_different_states() {
            // given
            var operation1 = createMockEmbeddingOperation("batches/batch-1", BatchJobState.BATCH_STATE_SUCCEEDED);
            var operation2 = createMockEmbeddingOperation("batches/batch-2", BatchJobState.BATCH_STATE_FAILED);
            var operation3 = createMockEmbeddingOperation("batches/batch-3", BatchJobState.BATCH_STATE_CANCELLED);
            var listResponse = new ListOperationsResponse<>(List.of(operation1, operation2, operation3), null);

            when(mockGeminiService.<GeminiEmbeddingResponse>batchListBatches(null, null))
                    .thenReturn(listResponse);

            // when
            BatchList<Embedding> result = subject.listBatchJobs(null, null);

            // then
            assertThat(result.responses()).hasSize(3);
            verify(mockGeminiService).batchListBatches(null, null);
        }

        @Test
        void should_throw_exception_when_listing_fails() {
            // given
            when(mockGeminiService.<List<GeminiEmbeddingResponse>>batchListBatches(null, null))
                    .thenThrow(new RuntimeException("Server error"));

            // when & then
            assertThatThrownBy(() -> subject.listBatchJobs(null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Server error");
        }

        @ParameterizedTest
        @CsvSource({"1, Invalid page size", "100, Invalid page size", "1000, Invalid page size"})
        void should_throw_exception_when_invalid_page_size(Integer pageSize, String errorMessage) {
            // given
            when(mockGeminiService.<List<GeminiEmbeddingResponse>>batchListBatches(pageSize, null))
                    .thenThrow(new RuntimeException(errorMessage));

            // when & then
            assertThatThrownBy(() -> subject.listBatchJobs(pageSize, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(errorMessage);
        }

        @Test
        void should_throw_exception_when_invalid_page_token() {
            // given
            String invalidToken = "invalid-token";
            when(mockGeminiService.<List<GeminiEmbeddingResponse>>batchListBatches(null, invalidToken))
                    .thenThrow(new RuntimeException("Invalid page token"));

            // when & then
            assertThatThrownBy(() -> subject.listBatchJobs(null, invalidToken))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid page token");
        }

        private BatchRequestResponse.Operation<GeminiEmbeddingResponse> createMockEmbeddingOperation(
                String name, BatchJobState state) {
            // Helper method to create mock operations for embedding testing
            return new BatchRequestResponse.Operation<>(name, Map.of("state", state), false, null, null);
        }
    }

    @Nested
    class BatchEmbeddingSerialization {

        private static final String EMBEDDING_PENDING_RESPONSE =
                """
                {
                  "name": "batches/embed-test-123",
                  "metadata": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.EmbedContentBatch",
                    "model": "models/text-embedding-004",
                    "displayName": "embeddings-batch",
                    "state": "BATCH_STATE_PENDING",
                    "name": "batches/embed-test-123"
                  }
                }
                """;

        private static final String EMBEDDING_SUCCEEDED_RESPONSE =
                """
                {
                  "name": "batches/embed-test-123",
                  "metadata": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.EmbedContentBatch",
                    "model": "models/text-embedding-004",
                    "displayName": "embeddings-batch",
                    "state": "BATCH_STATE_SUCCEEDED",
                    "name": "batches/embed-test-123"
                  },
                  "done": true,
                  "response": {
                    "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.EmbedContentBatchOutput",
                    "inlinedResponses": {
                      "inlinedResponses": [
                        {
                          "response": {
                            "embedding": {
                              "values": [0.1, 0.2, 0.3, 0.4, 0.5]
                            }
                          }
                        },
                        {
                          "response": {
                            "embedding": {
                              "values": [0.6, 0.7, 0.8, 0.9, 1.0]
                            }
                          }
                        }
                      ]
                    }
                  }
                }
                """;

        @Test
        void should_deserialize_embedding_batch_response_with_error() {
            // given
            String EMBEDDING_ERROR_RESPONSE =
                    """
            {
              "name": "batches/embed-test-123",
              "metadata": {
                "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.EmbedContentBatch",
                "model": "models/text-embedding-004",
                "displayName": "embeddings-batch",
                "state": "BATCH_STATE_SUCCEEDED",
                "name": "batches/embed-test-123"
              },
              "done": true,
              "response": {
                "@type": "type.googleapis.com/google.ai.generativelanguage.v1main.EmbedContentBatchOutput",
                "inlinedResponses": {
                  "inlinedResponses": [
                    {
                      "response": {
                        "embedding": {
                          "values": [0.1, 0.2, 0.3, 0.4, 0.5]
                        }
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
                        "embedding": {
                          "values": [0.6, 0.7, 0.8, 0.9, 1.0]
                        }
                      }
                    }
                  ]
                }
              }
            }
            """;

            var mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(EMBEDDING_ERROR_RESPONSE)
                    .build());
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(API_KEY)
                    .modelName("gemini-embedding-001")
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();

            var batchName = new BatchName("batches/embed-test-123");

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var success = (BatchSuccess<Embedding>) result;
            assertThat(success.batchName().value()).isEqualTo("batches/embed-test-123");

            var results = success.responses();
            assertThat(results).hasSize(2);

            assertThat(results.get(0).dimension()).isEqualTo(5);
            assertThat(results.get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);

            assertThat(results.get(1).dimension()).isEqualTo(5);
            assertThat(results.get(1).vector()).containsExactly(0.6f, 0.7f, 0.8f, 0.9f, 1.0f);

            assertThat(success.errors()).hasSize(1);
            assertThat(success.errors().get(0).code()).isEqualTo(4);
            assertThat(success.errors().get(0).message())
                    .isEqualTo("Deadline expired before operation could complete.");
        }

        @Test
        void should_deserialize_pending_embedding_batch_response() {
            // given
            var mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(EMBEDDING_PENDING_RESPONSE)
                    .build());
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(API_KEY)
                    .modelName("gemini-embedding-001")
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();

            var segments = List.of(TextSegment.from("Test segment 1"), TextSegment.from("Test segment 2"));

            // when
            var result = subject.createBatchInline("embeddings-batch", 0L, segments);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);
            var incomplete = (BatchIncomplete<?>) result;
            assertThat(incomplete.batchName().value()).isEqualTo("batches/embed-test-123");
            assertThat(incomplete.state()).isEqualTo(BATCH_STATE_PENDING);
        }

        @Test
        void should_deserialize_succeeded_embedding_batch_response() {
            // given
            var mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .body(EMBEDDING_SUCCEEDED_RESPONSE)
                    .build());
            var subject = GoogleAiGeminiBatchEmbeddingModel.builder()
                    .apiKey(API_KEY)
                    .modelName("gemini-embedding-001")
                    .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                    .build();

            var batchName = new BatchName("batches/embed-test-123");

            // when
            var result = subject.retrieveBatchResults(batchName);

            // then
            assertThat(result).isInstanceOf(BatchSuccess.class);
            var success = (BatchSuccess<Embedding>) result;
            assertThat(success.batchName().value()).isEqualTo("batches/embed-test-123");

            var results = success.responses();
            assertThat(results).hasSize(2);

            assertThat(results.get(0).dimension()).isEqualTo(5);
            assertThat(results.get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);

            assertThat(results.get(1).dimension()).isEqualTo(5);
            assertThat(results.get(1).vector()).containsExactly(0.6f, 0.7f, 0.8f, 0.9f, 1.0f);
        }
    }

    private static BatchRequestResponse.Operation<GeminiEmbeddingResponse> createSuccessOperation(
            String operationName, List<Embedding> embeddings) {
        var inlinedResponses = embeddings.stream()
                .map(embedding -> new GeminiEmbeddingResponse(
                        new GeminiEmbeddingResponse.GeminiEmbeddingResponseValues(embedding.vectorAsList())))
                .map(response -> new InlinedResponseWrapper<>(response, null))
                .toList();

        var response = new BatchCreateResponse<>(
                "type.googleapis.com/google.ai.generativelanguage.v1main.EmbedContentBatchOutput",
                new BatchCreateResponse.InlinedResponses<>(inlinedResponses));

        return new BatchRequestResponse.Operation<>(
                operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, response);
    }

    private static BatchRequestResponse.Operation<GeminiEmbeddingResponse> createSuccessOperationWithError(
            String operationName, List<Embedding> embeddings, BatchRequestResponse.Operation.Status error) {
        List<InlinedResponseWrapper<GeminiEmbeddingResponse>> inlinedResponses = new ArrayList<>();

        // Add first successful response
        if (!embeddings.isEmpty()) {
            var firstResponse = new GeminiEmbeddingResponse(new GeminiEmbeddingResponse.GeminiEmbeddingResponseValues(
                    embeddings.get(0).vectorAsList()));
            inlinedResponses.add(new InlinedResponseWrapper<>(firstResponse, null));
        }

        // Add error
        inlinedResponses.add(new InlinedResponseWrapper<>(null, error));

        // Add remaining successful responses
        for (int i = 1; i < embeddings.size(); i++) {
            var response = new GeminiEmbeddingResponse(new GeminiEmbeddingResponse.GeminiEmbeddingResponseValues(
                    embeddings.get(i).vectorAsList()));
            inlinedResponses.add(new InlinedResponseWrapper<>(response, null));
        }

        var response = new BatchCreateResponse<>(
                "type.googleapis.com/google.ai.generativelanguage.v1main.EmbedContentBatchOutput",
                new BatchCreateResponse.InlinedResponses<>(inlinedResponses));

        return new BatchRequestResponse.Operation<>(
                operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, response);
    }

    private static BatchRequestResponse.Operation<GeminiEmbeddingResponse> createSuccessOperationWithNullResponse(
            String operationName) {
        return new BatchRequestResponse.Operation<>(
                operationName, Map.of("state", BATCH_STATE_SUCCEEDED.name()), true, null, null);
    }

    private static BatchRequestResponse.Operation<GeminiEmbeddingResponse> createErrorOperation(
            String operationName, int errorCode, String errorMessage, List<Map<String, Object>> errorDetails) {
        var errorStatus = new BatchRequestResponse.Operation.Status(errorCode, errorMessage, errorDetails);
        return new BatchRequestResponse.Operation<>(
                operationName, Map.of("state", BATCH_STATE_FAILED.name()), true, errorStatus, null);
    }

    private static BatchRequestResponse.Operation<GeminiEmbeddingResponse> createCancelledOperation(
            String operationName, String errorMessage) {
        var errorStatus = new BatchRequestResponse.Operation.Status(13, errorMessage, List.of());
        return new BatchRequestResponse.Operation<>(
                operationName, Map.of("state", BATCH_STATE_CANCELLED.name()), true, errorStatus, null);
    }

    private GoogleAiGeminiBatchEmbeddingModel createSubject() {
        return new GoogleAiGeminiBatchEmbeddingModel(
                GoogleAiGeminiBatchEmbeddingModel.builder()
                        .apiKey("apiKey")
                        .modelName(MODEL_NAME)
                        .taskType(TaskType.RETRIEVAL_DOCUMENT),
                mockGeminiService);
    }

    private static BatchRequestResponse.Operation<GeminiEmbeddingResponse> createPendingOperation(
            String operationName, BatchJobState state) {
        return new BatchRequestResponse.Operation<>(operationName, Map.of("state", state.name()), false, null, null);
    }
}
