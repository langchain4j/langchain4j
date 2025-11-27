package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.model.googleai.GeminiFiles.GeminiFile;
import dev.langchain4j.model.googleai.GeminiFiles.GeminiUploadFailureException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GeminiFilesTest {
    private static final String TEST_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String TEST_API_KEY = "test-api-key";
    private static final String TEST_UPLOAD_URL = "https://generativelanguage.googleapis.com/upload/session/123";

    @Mock
    HttpClient mockHttpClient;

    @TempDir
    Path tempDir;

    @Captor
    ArgumentCaptor<HttpRequest> requestCaptor;

    @Nested
    class UploadFileTest {

        @Test
        void should_uploadFileSuccessfully() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("test.txt", "Hello, Gemini!");
            var expectedFileUri = "https://generativelanguage.googleapis.com/v1beta/files/test-file-123";

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(TEST_UPLOAD_URL))
                    .thenAnswer(invocation -> createFileUploadResponse(expectedFileUri, "test.txt", "text/plain", 15L));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            var uploadedFile = subject.uploadFile(testFile, "test.txt");

            // Then
            assertThat(uploadedFile)
                    .isEqualTo(new GeminiFile(
                            "files/test.txt",
                            "test.txt",
                            "text/plain",
                            15L,
                            "2025-01-01T10:00:00Z",
                            "2025-01-01T10:00:00Z",
                            "2025-01-03T10:00:00Z",
                            "abc123def456",
                            expectedFileUri,
                            "ACTIVE"));
        }

        @Test
        void should_uploadFileWithoutDisplayName() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("sample.mp3", "audio content");
            var expectedFileUri = "https://generativelanguage.googleapis.com/v1beta/files/audio-file-456";

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(TEST_UPLOAD_URL))
                    .thenAnswer(
                            invocation -> createFileUploadResponse(expectedFileUri, "sample.mp3", "audio/mpeg", 13L));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            var uploadedFile = subject.uploadFile(testFile, null);

            // Then
            assertThat(uploadedFile)
                    .isEqualTo(new GeminiFile(
                            "files/sample.mp3",
                            "sample.mp3",
                            "audio/mpeg",
                            13L,
                            "2025-01-01T10:00:00Z",
                            "2025-01-01T10:00:00Z",
                            "2025-01-03T10:00:00Z",
                            "abc123def456",
                            expectedFileUri,
                            "ACTIVE"));
        }

        @Test
        void should_sendCorrectInitialUploadRequest() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("document.pdf", "PDF content here");

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(TEST_UPLOAD_URL))
                    .thenAnswer(invocation -> createFileUploadResponse(
                            "https://generativelanguage.googleapis.com/v1beta/files/doc-789",
                            "document.pdf",
                            "application/pdf",
                            16L));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            subject.uploadFile(testFile, "My Document");

            // Then
            verify(mockHttpClient, times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
            var initialRequest = requestCaptor.getAllValues().get(0);

            assertThat(initialRequest.uri()).hasToString(TEST_BASE_URL + "/upload/v1beta/files");
            assertThat(initialRequest.headers().map())
                    .containsAllEntriesOf(java.util.Map.of(
                            "x-goog-api-key", List.of(TEST_API_KEY),
                            "content-type", List.of("application/json"),
                            "user-agent", List.of("LangChain4j"),
                            "x-goog-upload-protocol", List.of("resumable"),
                            "x-goog-upload-command", List.of("start"),
                            "x-goog-upload-header-content-length", List.of("16")));
            assertThat(initialRequest.headers().firstValue("x-goog-upload-header-content-type"))
                    .isPresent();
        }

        @Test
        void should_sendCorrectFileUploadRequest() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("image.jpg", "fake image data");
            var uploadUrl = TEST_UPLOAD_URL;
            var fileBytes = Files.readAllBytes(testFile);

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(uploadUrl))
                    .thenAnswer(invocation -> createFileUploadResponse(
                            "https://generativelanguage.googleapis.com/v1beta/files/img-999",
                            "image.jpg",
                            "image/jpeg",
                            15L));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            subject.uploadFile(testFile, "Test Image");

            // Then
            verify(mockHttpClient, times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

            var uploadRequest = requestCaptor.getAllValues().get(1);
            assertThat(uploadRequest.uri()).hasToString(uploadUrl);
            assertThat(uploadRequest.headers().firstValue("x-goog-upload-offset"))
                    .contains("0");
            assertThat(uploadRequest.headers().firstValue("x-goog-upload-command"))
                    .contains("upload, finalize");

            // Verify body publisher contains the correct bytes
            assertThat(uploadRequest.bodyPublisher()).isPresent();
            //noinspection OptionalGetWithoutIsPresent
            assertThat(uploadRequest.bodyPublisher().get().contentLength()).isEqualTo(fileBytes.length);
        }

        @Test
        void should_handleLargeFile() throws IOException, InterruptedException {
            // Given
            var largeContent = "x".repeat(1024 * 1024); // 1 MB
            var testFile = createTestFile("large.bin", largeContent);

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(TEST_UPLOAD_URL))
                    .thenAnswer(invocation -> createFileUploadResponse(
                            "https://generativelanguage.googleapis.com/v1beta/files/large-file",
                            "large.bin",
                            "application/octet-stream",
                            (long) largeContent.length()));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            var uploadedFile = subject.uploadFile(testFile, "Large File");

            // Then
            assertThat(uploadedFile).isNotNull();
            assertThat(uploadedFile.sizeBytes()).isEqualTo(largeContent.length());
        }

        @Test
        void should_throwExceptionWhenUploadUrlNotFound() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("test.txt", "content");

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createResponseWithoutUploadUrl());

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(() -> subject.uploadFile(testFile, "Test"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Upload URL not found in response headers");
        }

        @Test
        void should_throwExceptionWhenFilePathIsNull() {
            // Given
            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(() -> subject.uploadFile(null, "Test"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("filePath");
        }

        @Test
        void should_throwExceptionWhenFileDoesNotExist() {
            // Given
            var nonExistentFile = tempDir.resolve("does-not-exist.txt");
            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(() -> subject.uploadFile(nonExistentFile, "Test"))
                    .isInstanceOf(IOException.class);
        }

        @Test
        void should_throwGeminiUploadFailureExceptionWhenHttpClientThrowsIOException()
                throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("test.txt", "content");

            // Simulate IOException when sending the request
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Network error"));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(() -> subject.uploadFile(testFile, "Test"))
                    .isInstanceOf(GeminiUploadFailureException.class)
                    .hasMessageContaining("Failed to initiate resumable upload")
                    .hasCauseInstanceOf(IOException.class)
                    .hasRootCauseMessage("Network error");
        }

        @Test
        void should_handleFileInProcessingState() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("video.mp4", "video content");

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(TEST_UPLOAD_URL))
                    .thenAnswer(invocation -> createFileUploadResponseWithState(
                            "https://generativelanguage.googleapis.com/v1beta/files/video-123",
                            "video.mp4",
                            "video/mp4",
                            13L,
                            "PROCESSING"));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            var uploadedFile = subject.uploadFile(testFile, "Video");

            // Then
            assertThat(uploadedFile.state()).isEqualTo("PROCESSING");
            assertThat(uploadedFile.isProcessing()).isTrue();
            assertThat(uploadedFile.isActive()).isFalse();
        }
    }

    @Nested
    class ListFilesTest {

        @Test
        void should_listFilesSuccessfully() throws IOException, InterruptedException {
            // Given
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createListFilesResponse());

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            var files = subject.listFiles();

            // Then
            assertThat(files).hasSize(2);
            assertThat(files.get(0).name()).isEqualTo("files/file1");
            assertThat(files.get(0).displayName()).isEqualTo("File 1");
            assertThat(files.get(1).name()).isEqualTo("files/file2");
            assertThat(files.get(1).displayName()).isEqualTo("File 2");
        }

        @Test
        void should_sendCorrectListFilesRequest() throws IOException, InterruptedException {
            // Given
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createListFilesResponse());

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            subject.listFiles();

            // Then
            verify(mockHttpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
            var request = requestCaptor.getValue();

            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.uri()).hasToString(TEST_BASE_URL + "/v1beta/files");
            assertThat(request.headers().firstValue("x-goog-api-key")).contains(TEST_API_KEY);
        }

        @Test
        void should_returnEmptyListWhenNoFiles() throws IOException, InterruptedException {
            // Given
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createEmptyListFilesResponse());

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            var files = subject.listFiles();

            // Then
            assertThat(files).isEmpty();
        }

        @Test
        void should_throwGeminiUploadFailureExceptionWhenListFilesThrowsIOException()
                throws IOException, InterruptedException {
            // Given
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Network error"));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(subject::listFiles)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Network error");
        }
    }

    @Nested
    class DeleteFileTest {

        @Test
        void should_deleteFileSuccessfully() throws IOException, InterruptedException {
            // Given
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createSuccessfulDeleteResponse());

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            subject.deleteFile("files/test-file-123");

            // Then
            verify(mockHttpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
            var request = requestCaptor.getValue();

            assertThat(request.method()).isEqualTo("DELETE");
            assertThat(request.uri()).hasToString(TEST_BASE_URL + "/v1beta/files/test-file-123");
            assertThat(request.headers().firstValue("x-goog-api-key")).contains(TEST_API_KEY);
        }

        @Test
        void should_throwExceptionWhenFileNameIsNull() {
            // Given
            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(() -> subject.deleteFile(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }

        @Test
        void should_throwExceptionWhenFileNameIsBlank() {
            // Given
            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(() -> subject.deleteFile(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name");
        }

        @Test
        void should_throwExceptionWhenDeleteReturnsErrorStatusCode() throws IOException, InterruptedException {
            // Given
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createFailedDeleteResponse());

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(() -> subject.deleteFile("files/test-file-123"))
                    .isInstanceOf(GeminiUploadFailureException.class)
                    .hasMessageContaining("Failed to delete file")
                    .hasMessageContaining("files/test-file-123")
                    .hasMessageContaining("404");
        }

        @Test
        void should_throwGeminiUploadFailureExceptionWhenDeleteThrowsIOException()
                throws IOException, InterruptedException {
            // Given
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new IOException("Network error"));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When/Then
            assertThatThrownBy(() -> subject.deleteFile("files/test-file-123"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Network error");
        }
    }

    @Nested
    class ConstructorTest {

        @Test
        void should_useDefaultHttpClientWhenNull() {
            // When
            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // Then
            assertThat(subject).isNotNull();
        }

        @Test
        void should_useDefaultBaseUrlWhenNull() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("test.txt", "content");

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(TEST_UPLOAD_URL))
                    .thenAnswer(invocation -> createFileUploadResponse(
                            "https://generativelanguage.googleapis.com/v1beta/files/test",
                            "test.txt",
                            "text/plain",
                            7L));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .build();

            // When
            subject.uploadFile(testFile, "Test");

            // Then
            verify(mockHttpClient, times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
            var initialRequest = requestCaptor.getAllValues().get(0);
            assertThat(initialRequest.uri().toString())
                    .startsWith("https://generativelanguage.googleapis.com/upload/v1beta/files");
        }

        @Test
        void should_throwExceptionWhenApiKeyIsNull() {
            // When/Then
            assertThatThrownBy(() -> GeminiFiles.builder()
                            .httpClient(mockHttpClient)
                            .baseUrl(TEST_BASE_URL)
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apiKey");
        }

        @Test
        void should_throwExceptionWhenApiKeyIsBlank() {
            // When/Then
            assertThatThrownBy(() -> GeminiFiles.builder()
                            .apiKey("")
                            .httpClient(mockHttpClient)
                            .baseUrl(TEST_BASE_URL)
                            .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apiKey");
        }
    }

    @Nested
    class FileStateTest {

        @Test
        void should_detectActiveState() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("active.txt", "content");

            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(TEST_UPLOAD_URL))
                    .thenAnswer(invocation -> createFileUploadResponseWithState(
                            "https://generativelanguage.googleapis.com/v1beta/files/active-file",
                            "active.txt",
                            "text/plain",
                            7L,
                            "ACTIVE"));

            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            var uploadedFile = subject.uploadFile(testFile, "Active File");

            // Then
            assertThat(uploadedFile.isActive()).isTrue();
            assertThat(uploadedFile.isProcessing()).isFalse();
            assertThat(uploadedFile.isFailed()).isFalse();
        }

        @Test
        void should_detectFailedState() throws IOException, InterruptedException {
            // Given
            var testFile = createTestFile("failed.txt", "content");
            when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenAnswer(invocation -> createInitialUploadResponse(TEST_UPLOAD_URL))
                    .thenAnswer(invocation -> createFileUploadResponseWithState(
                            "https://generativelanguage.googleapis.com/v1beta/files/failed-file",
                            "failed.txt",
                            "text/plain",
                            7L,
                            "FAILED"));
            var subject = GeminiFiles.builder()
                    .apiKey(TEST_API_KEY)
                    .httpClient(mockHttpClient)
                    .baseUrl(TEST_BASE_URL)
                    .build();

            // When
            var uploadedFile = subject.uploadFile(testFile, "Failed File");

            // Then
            assertThat(uploadedFile.isFailed()).isTrue();
            assertThat(uploadedFile.isActive()).isFalse();
            assertThat(uploadedFile.isProcessing()).isFalse();
        }
    }

    private Path createTestFile(String filename, String content) throws IOException {
        var file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }

    private HttpResponse<String> createInitialUploadResponse(String uploadUrl) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.headers())
                .thenReturn(java.net.http.HttpHeaders.of(
                        java.util.Map.of("x-goog-upload-url", List.of(uploadUrl)), (a, b) -> true));
        return response;
    }

    private HttpResponse<String> createResponseWithoutUploadUrl() {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (a, b) -> true));
        return response;
    }

    private HttpResponse<String> createFileUploadResponse(
            String uri, String displayName, String mimeType, Long sizeBytes) {
        return createFileUploadResponseWithState(uri, displayName, mimeType, sizeBytes, "ACTIVE");
    }

    private HttpResponse<String> createFileUploadResponseWithState(
            String uri, String displayName, String mimeType, Long sizeBytes, String state) {
        var fileJson = String.format(
                """
                        {
                          "file": {
                            "name": "files/%s",
                            "displayName": "%s",
                            "mimeType": "%s",
                            "sizeBytes": "%d",
                            "createTime": "2025-01-01T10:00:00Z",
                            "updateTime": "2025-01-01T10:00:00Z",
                            "expirationTime": "2025-01-03T10:00:00Z",
                            "sha256Hash": "abc123def456",
                            "uri": "%s",
                            "state": "%s"
                          }
                        }
                        """,
                displayName, displayName, mimeType, sizeBytes, uri, state);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(fileJson);
        return response;
    }

    private HttpResponse<String> createListFilesResponse() {
        var json =
                """
                {
                  "files": [
                    {
                      "name": "files/file1",
                      "displayName": "File 1",
                      "mimeType": "text/plain",
                      "sizeBytes": "100",
                      "createTime": "2025-01-01T10:00:00Z",
                      "updateTime": "2025-01-01T10:00:00Z",
                      "expirationTime": "2025-01-03T10:00:00Z",
                      "sha256Hash": "hash1",
                      "uri": "https://example.com/file1",
                      "state": "ACTIVE"
                    },
                    {
                      "name": "files/file2",
                      "displayName": "File 2",
                      "mimeType": "image/jpeg",
                      "sizeBytes": "200",
                      "createTime": "2025-01-01T11:00:00Z",
                      "updateTime": "2025-01-01T11:00:00Z",
                      "expirationTime": "2025-01-03T11:00:00Z",
                      "sha256Hash": "hash2",
                      "uri": "https://example.com/file2",
                      "state": "PROCESSING"
                    }
                  ]
                }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(json);
        return response;
    }

    private HttpResponse<String> createEmptyListFilesResponse() {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.body()).thenReturn("{}");
        return response;
    }

    private HttpResponse<String> createSuccessfulDeleteResponse() {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        return response;
    }

    private HttpResponse<String> createFailedDeleteResponse() {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        return response;
    }
}
