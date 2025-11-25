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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateRequest;
import dev.langchain4j.model.googleai.BatchRequestResponse.BatchCreateResponse;
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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import dev.langchain4j.model.googleai.jsonl.JsonLinesWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAiGeminiBatchEmbeddingModelTest {
    public static final String MODEL_NAME = "gemini-embedding-001";

    @Mock
    private GeminiService mockGeminiService;

    @Captor
    private ArgumentCaptor<BatchCreateRequest<GeminiEmbeddingRequest>> batchRequestCaptor;

    private GoogleAiGeminiBatchEmbeddingModel subject;

    @BeforeEach
    void setUp() {
        subject = createSubject();
    }

    @Nested
    class CreateBatchInline {

        @Test
        void should_create_batch_with_valid_segments() {
            // given
            var displayName = "Test Embedding Batch";
            var priority = 1L;
            var segments =
                    List.of(TextSegment.from("What is machine learning?"), TextSegment.from("Explain neural networks"));
            var expectedOperation = createPendingOperation("batches/embed-test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            any(), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
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
                            any(), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
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
                            any(), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
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
                            any(), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
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
                            any(), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
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
                            any(), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
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
                            any(), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
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
                            any(), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchInline(displayName, priority, segments);

            // then
            assertThat(result).isInstanceOf(BatchIncomplete.class);

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), any(), eq(ASYNC_BATCH_EMBED_CONTENT));
        }
    }

    @Nested
    class CreateBatchFromFile {

        @Mock
        private GeminiFile mockGeminiFile;

        @Test
        void should_create_batch_from_file_with_valid_parameters() {
            // given
            String displayName = "Batch from File";
            Long priority = 1L;
            when(mockGeminiFile.name()).thenReturn("files/test-file-123");
            var expectedOperation = createPendingOperation("batches/embed-file-test-123", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                    eq(MODEL_NAME), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, priority, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/embed-file-test-123"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isEqualTo(priority);
            assertThat(capturedRequest.batch().inputConfig().fileName()).isEqualTo("files/test-file-123");
            assertThat(capturedRequest.batch().inputConfig().requests()).isNull();
        }

        @Test
        void should_create_batch_from_file_with_null_priority() {
            // given
            String displayName = "Batch from File with Null Priority";
            when(mockGeminiFile.name()).thenReturn("files/test-file-456");
            var expectedOperation = createPendingOperation("batches/embed-file-test-456", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                    eq(MODEL_NAME), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, null, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/embed-file-test-456"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().displayName()).isEqualTo(displayName);
            assertThat(capturedRequest.batch().priority()).isZero();
            assertThat(capturedRequest.batch().inputConfig().fileName()).isEqualTo("files/test-file-456");
        }

        @Test
        void should_create_batch_from_file_with_high_priority() {
            // given
            String displayName = "High Priority File Batch";
            Long priority = 100L;
            when(mockGeminiFile.name()).thenReturn("files/test-file-high-priority");
            var expectedOperation = createPendingOperation("batches/embed-file-high", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                    eq(MODEL_NAME), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, priority, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/embed-file-high"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().priority()).isEqualTo(100L);
        }

        @Test
        void should_create_batch_from_file_with_negative_priority() {
            // given
            String displayName = "Low Priority File Batch";
            Long priority = -50L;
            when(mockGeminiFile.name()).thenReturn("files/test-file-low-priority");
            var expectedOperation = createPendingOperation("batches/embed-file-low", BATCH_STATE_PENDING);
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                    eq(MODEL_NAME), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            var result = subject.createBatchFromFile(displayName, priority, mockGeminiFile);

            // then
            assertThat(result)
                    .isInstanceOf(BatchIncomplete.class)
                    .isEqualTo(new BatchIncomplete<>(new BatchName("batches/embed-file-low"), BATCH_STATE_PENDING));

            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), batchRequestCaptor.capture(), eq(ASYNC_BATCH_EMBED_CONTENT));

            var capturedRequest = batchRequestCaptor.getValue();
            assertThat(capturedRequest.batch().priority()).isEqualTo(-50L);
        }

        @Test
        void should_throw_exception_when_creating_batch_from_file_fails() {
            // given
            String displayName = "Batch from File";
            Long priority = 1L;
            when(mockGeminiFile.name()).thenReturn("files/test-file-error");
            when(mockGeminiService.<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                    eq(MODEL_NAME), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenThrow(new RuntimeException("Error creating batch from file"));

            // when & then
            assertThatThrownBy(() -> subject.createBatchFromFile(displayName, priority, mockGeminiFile))
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
                    eq(MODEL_NAME), any(), eq(ASYNC_BATCH_EMBED_CONTENT)))
                    .thenReturn(expectedOperation);

            // when
            subject.createBatchFromFile(displayName, priority, mockGeminiFile);

            // then
            verify(mockGeminiService)
                    .<GeminiEmbeddingRequest, GeminiEmbeddingResponse>batchCreate(
                            eq(MODEL_NAME), any(), eq(ASYNC_BATCH_EMBED_CONTENT));
        }
    }

    @Nested
    class WriteBatchToFile {

        @Mock
        private JsonLinesWriter mockJsonLinesWriter;

        @Captor
        private ArgumentCaptor<GeminiEmbeddingRequest> embeddingRequestCaptor;

        @Test
        void should_write_single_request_to_file() throws Exception {
            // given
            var segment = TextSegment.from("Sample text");
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            subject.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter).write(embeddingRequestCaptor.capture());
            var capturedRequest = embeddingRequestCaptor.getValue();
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
            subject.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter, times(3)).write(embeddingRequestCaptor.capture());
            var capturedRequests = embeddingRequestCaptor.getAllValues();
            assertThat(capturedRequests).hasSize(3);
            assertThat(capturedRequests.get(0).content().parts().get(0).text()).isEqualTo("First text");
            assertThat(capturedRequests.get(1).content().parts().get(0).text()).isEqualTo("Second text");
            assertThat(capturedRequests.get(2).content().parts().get(0).text()).isEqualTo("Third text");
        }

        @Test
        void should_write_request_with_metadata_to_file() throws Exception {
            // given
            var segment = TextSegment.from("Document text", Metadata.from("title", "Document Title"));
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            subject.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter).write(embeddingRequestCaptor.capture());
            var capturedRequest = embeddingRequestCaptor.getValue();
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
            subject.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter).write(embeddingRequestCaptor.capture());
            var capturedRequest = embeddingRequestCaptor.getValue();
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
            subject.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter).write(embeddingRequestCaptor.capture());
            var capturedRequest = embeddingRequestCaptor.getValue();
            assertThat(capturedRequest.title()).isNull();
        }

        @Test
        void should_handle_empty_requests_list() throws Exception {
            // given
            List<BatchFileRequest<TextSegment>> requests = List.of();

            // when
            subject.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter, never()).write(any());
        }

        @Test
        void should_throw_exception_when_writing_to_file_fails() throws Exception {
            // given
            var segment = TextSegment.from("Sample text");
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);
            doThrow(new IOException("Error writing to file")).when(mockJsonLinesWriter).write(any());

            // when & then
            assertThatThrownBy(() -> subject.writeBatchToFile(mockJsonLinesWriter, requests))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Error writing to file");
        }

        @Test
        void should_include_task_type_in_written_request() throws Exception {
            // given
            var segment = TextSegment.from("Sample text");
            var request = new BatchFileRequest<>("key-1", segment);
            var requests = List.of(request);

            // when
            subject.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter).write(embeddingRequestCaptor.capture());
            var capturedRequest = embeddingRequestCaptor.getValue();
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
            subjectWithDimensionality.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter).write(embeddingRequestCaptor.capture());
            var capturedRequest = embeddingRequestCaptor.getValue();
            assertThat(capturedRequest.outputDimensionality()).isEqualTo(256);
        }

        @Test
        void should_write_requests_with_different_text_lengths() throws Exception {
            // given
            var shortSegment = TextSegment.from("Short");
            var mediumSegment = TextSegment.from("This is a medium length text segment for testing");
            var longSegment = TextSegment.from(
                    "This is a very long text segment that contains much more content "
                            + "and is designed to test how the writer handles larger text inputs "
                            + "with multiple sentences and various punctuation marks.");
            var requests = List.of(
                    new BatchFileRequest<>("key-1", shortSegment),
                    new BatchFileRequest<>("key-2", mediumSegment),
                    new BatchFileRequest<>("key-3", longSegment));

            // when
            subject.writeBatchToFile(mockJsonLinesWriter, requests);

            // then
            verify(mockJsonLinesWriter, times(3)).write(embeddingRequestCaptor.capture());
            var capturedRequests = embeddingRequestCaptor.getAllValues();
            assertThat(capturedRequests).hasSize(3);
            assertThat(capturedRequests.get(0).content().parts().get(0).text()).isEqualTo("Short");
            assertThat(capturedRequests.get(1).content().parts().get(0).text())
                    .isEqualTo("This is a medium length text segment for testing");
            assertThat(capturedRequests.get(2).content().parts().get(0).text()).contains("very long text segment");
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

    private static BatchRequestResponse.Operation<GeminiEmbeddingResponse> createSuccessOperation(
            String operationName, List<Embedding> embeddings) {
        var inlinedResponses = embeddings.stream()
                .map(embedding -> new GeminiEmbeddingResponse(
                        new GeminiEmbeddingResponse.GeminiEmbeddingResponseValues(embedding.vectorAsList())))
                .map(BatchCreateResponse.InlinedResponseWrapper::new)
                .toList();

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
