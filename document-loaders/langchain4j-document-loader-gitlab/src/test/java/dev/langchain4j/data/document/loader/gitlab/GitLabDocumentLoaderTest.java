package dev.langchain4j.data.document.loader.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GitLabDocumentLoaderTest {

    @Test
    void shouldLoadTwoDocumentsFromRepositoryTree() {
        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse treeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(repositoryTreeResponseBody())
                .build();

        SuccessfulHttpResponse readmeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(readmeRawContent(), StandardCharsets.UTF_8))
                .build();

        SuccessfulHttpResponse mainJavaResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(mainJavaRawContent(), StandardCharsets.UTF_8))
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.execute(requestCaptor.capture())).thenReturn(treeResponse, readmeResponse, mainJavaResponse);

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        List<Document> documents = loader.loadDocuments(parser);

        assertThat(documents).hasSize(2);

        Document readme = documents.get(0);
        assertThat(readme.text()).isEqualTo("README content");
        assertThat(readme.metadata().getString(GitLabDocumentLoader.METADATA_GITLAB_PROJECT_ID))
                .isEqualTo("123");
        assertThat(readme.metadata().getString(Document.FILE_NAME)).isEqualTo("README.md");
        assertThat(readme.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.com/projects/123/-/blob/main/README.md");

        Document mainJava = documents.get(1);
        assertThat(mainJava.text()).isEqualTo("public class Main {}");
        assertThat(mainJava.metadata().getString(GitLabDocumentLoader.METADATA_GITLAB_PROJECT_ID))
                .isEqualTo("123");
        assertThat(mainJava.metadata().getString(Document.FILE_NAME)).isEqualTo("Main.java");
        assertThat(mainJava.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.com/projects/123/-/blob/main/src/Main.java");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(3);

        assertThat(requests.get(0).url())
                .isEqualTo(
                        "https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=1&ref=main");
        assertThat(requests.get(1).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/README.md/raw?ref=main");
        assertThat(requests.get(2).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/src%2FMain.java/raw?ref=main");

        assertThat(requests.get(0).headers().get("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(1).headers().get("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(2).headers().get("PRIVATE-TOKEN")).contains("token");
    }

    @Test
    void shouldHandlePaginationWhenListingRepositoryTree() {
        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse treePage1Response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(repositoryTreeResponseBodyPage1())
                .headers(Map.of("X-Next-Page", List.of("2")))
                .build();

        SuccessfulHttpResponse treePage2Response = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(repositoryTreeResponseBodyPage2())
                .headers(Map.of("X-Next-Page", List.of("")))
                .build();

        SuccessfulHttpResponse readmeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(readmeRawContent(), StandardCharsets.UTF_8))
                .build();

        SuccessfulHttpResponse mainJavaResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(mainJavaRawContent(), StandardCharsets.UTF_8))
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.execute(requestCaptor.capture()))
                .thenReturn(treePage1Response, treePage2Response, readmeResponse, mainJavaResponse);

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        List<Document> documents = loader.loadDocuments(parser);

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).text()).isEqualTo("README content");
        assertThat(documents.get(1).text()).isEqualTo("public class Main {}");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(4);

        assertThat(requests.get(0).url())
                .isEqualTo(
                        "https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=1&ref=main");
        assertThat(requests.get(1).url())
                .isEqualTo(
                        "https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=2&ref=main");
        assertThat(requests.get(2).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/README.md/raw?ref=main");
        assertThat(requests.get(3).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/src%2FMain.java/raw?ref=main");

        assertThat(requests.get(0).headers().get("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(1).headers().get("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(2).headers().get("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(3).headers().get("PRIVATE-TOKEN")).contains("token");
    }

    @Test
    void shouldFallbackFromMainToMasterWhenBranchNotFound() {
        HttpClient httpClient = mock(HttpClient.class);

        // First call for 'main' fails with 404
        HttpException notFoundException = new HttpException(404, "Not Found");

        SuccessfulHttpResponse treeMasterResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(repositoryTreeResponseBody())
                .build();

        SuccessfulHttpResponse readmeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(readmeRawContent(), StandardCharsets.UTF_8))
                .build();

        SuccessfulHttpResponse mainJavaResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(mainJavaRawContent(), StandardCharsets.UTF_8))
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.execute(requestCaptor.capture()))
                .thenThrow(notFoundException)
                .thenReturn(treeMasterResponse, readmeResponse, mainJavaResponse);

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        List<Document> documents = loader.loadDocuments(parser);

        assertThat(documents).hasSize(2);

        Document readme = documents.get(0);
        assertThat(readme.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.com/projects/123/-/blob/master/README.md");

        Document mainJava = documents.get(1);
        assertThat(mainJava.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.com/projects/123/-/blob/master/src/Main.java");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(4);

        assertThat(requests.get(0).url())
                .isEqualTo(
                        "https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=1&ref=main");
        assertThat(requests.get(1).url())
                .isEqualTo(
                        "https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=1&ref=master");
        assertThat(requests.get(2).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/README.md/raw?ref=master");
        assertThat(requests.get(3).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/src%2FMain.java/raw?ref=master");
    }

    @Test
    void shouldBuildUrlsForProjectPath() {
        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse treeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(repositoryTreeResponseBodyReadmeOnly())
                .build();

        SuccessfulHttpResponse readmeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(readmeRawContent(), StandardCharsets.UTF_8))
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.execute(requestCaptor.capture())).thenReturn(treeResponse, readmeResponse);

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.example.com")
                .projectId("group/project")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        List<Document> documents = loader.loadDocuments(parser);

        assertThat(documents).hasSize(1);

        Document readme = documents.get(0);
        assertThat(readme.metadata().getString(GitLabDocumentLoader.METADATA_GITLAB_PROJECT_ID))
                .isEqualTo("group/project");
        assertThat(readme.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.example.com/group/project/-/blob/main/README.md");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(2);

        assertThat(requests.get(0).url())
                .isEqualTo(
                        "https://gitlab.example.com/api/v4/projects/group%2Fproject/repository/tree?recursive=true&per_page=100&page=1&ref=main");
        assertThat(requests.get(1).url())
                .isEqualTo(
                        "https://gitlab.example.com/api/v4/projects/group%2Fproject/repository/files/README.md/raw?ref=main");
    }

    @Test
    void shouldThrowWhenTreeRequestReturnsNon2xx() {
        HttpClient httpClient = mock(HttpClient.class);

        when(httpClient.execute(any(HttpRequest.class))).thenThrow(new HttpException(500, "Boom"));

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        assertThatThrownBy(() -> loader.loadDocuments("main", null, true, parser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("status code 500");
    }

    @Test
    void shouldThrowWhenTreeResponseIsInvalidJson() {
        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse treeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body("not-json")
                .build();

        when(httpClient.execute(any(HttpRequest.class))).thenReturn(treeResponse);

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        assertThatThrownBy(() -> loader.loadDocuments("main", null, true, parser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse GitLab API response");
    }

    @Test
    void shouldThrowWhenTreeRequestFails() {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.execute(any(HttpRequest.class))).thenThrow(new RuntimeException("boom"));

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        assertThatThrownBy(() -> loader.loadDocuments("main", null, true, parser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to call GitLab API");
    }

    @Test
    void shouldSkipFileWhenRawRequestFails() {
        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse treeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(repositoryTreeResponseBody())
                .build();

        HttpException notFoundException = new HttpException(404, "Not Found");

        SuccessfulHttpResponse mainJavaResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(mainJavaRawContent(), StandardCharsets.UTF_8))
                .build();

        when(httpClient.execute(any(HttpRequest.class)))
                .thenReturn(treeResponse)
                .thenThrow(notFoundException)
                .thenReturn(mainJavaResponse);

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        List<Document> documents = loader.loadDocuments("main", null, true, parser);

        assertThat(documents).hasSize(1);
        Document mainJava = documents.get(0);
        assertThat(mainJava.text()).isEqualTo("public class Main {}");
        assertThat(mainJava.metadata().getString(Document.FILE_NAME)).isEqualTo("Main.java");
    }

    @Test
    void shouldFallbackToMasterWhenLoadingSingleDocument() {
        HttpClient httpClient = mock(HttpClient.class);

        HttpException notFoundException = new HttpException(404, "Not Found");

        SuccessfulHttpResponse masterResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(readmeRawContent(), StandardCharsets.UTF_8))
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.execute(requestCaptor.capture()))
                .thenThrow(notFoundException)
                .thenReturn(masterResponse);

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        Document document = loader.loadDocument("README.md", parser);

        assertThat(document.text()).isEqualTo("README content");
        assertThat(document.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.com/projects/123/-/blob/master/README.md");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/README.md/raw?ref=main");
        assertThat(requests.get(1).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/README.md/raw?ref=master");

        assertThat(requests.get(0).headers().get("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(1).headers().get("PRIVATE-TOKEN")).contains("token");
    }

    @Test
    void shouldIncludePathAndNonRecursiveWhenListingRepositoryTree() {
        HttpClient httpClient = mock(HttpClient.class);

        SuccessfulHttpResponse treeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(repositoryTreeResponseBodyDocsReadmeOnly())
                .build();

        SuccessfulHttpResponse readmeResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(new String(readmeRawContent(), StandardCharsets.UTF_8))
                .build();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.execute(requestCaptor.capture())).thenReturn(treeResponse, readmeResponse);

        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl("https://gitlab.com")
                .projectId("123")
                .personalAccessToken("token")
                .httpClient(httpClient)
                .build();

        DocumentParser parser = inputStream -> {
            try {
                return Document.from(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        List<Document> documents = loader.loadDocuments("main", "docs", false, parser);

        assertThat(documents).hasSize(1);
        Document readme = documents.get(0);
        assertThat(readme.text()).isEqualTo("README content");
        assertThat(readme.metadata().getString(Document.FILE_NAME)).isEqualTo("README.md");
        assertThat(readme.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.com/projects/123/-/blob/main/docs/README.md");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).url())
                .isEqualTo(
                        "https://gitlab.com/api/v4/projects/123/repository/tree?recursive=false&per_page=100&page=1&ref=main&path=docs");
        assertThat(requests.get(1).url())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/docs%2FREADME.md/raw?ref=main");
    }

    private static String repositoryTreeResponseBody() {
        return "["
                + "{\"type\":\"blob\",\"path\":\"README.md\"},"
                + "{\"type\":\"blob\",\"path\":\"src/Main.java\"}"
                + "]";
    }

    private static String repositoryTreeResponseBodyPage1() {
        return "[" + "{\"type\":\"blob\",\"path\":\"README.md\"}" + "]";
    }

    private static String repositoryTreeResponseBodyPage2() {
        return "[" + "{\"type\":\"blob\",\"path\":\"src/Main.java\"}" + "]";
    }

    private static String repositoryTreeResponseBodyReadmeOnly() {
        return "[" + "{\"type\":\"blob\",\"path\":\"README.md\"}" + "]";
    }

    private static String repositoryTreeResponseBodyDocsReadmeOnly() {
        return "[" + "{\"type\":\"blob\",\"path\":\"docs/README.md\"}" + "]";
    }

    private static byte[] readmeRawContent() {
        return "README content".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] mainJavaRawContent() {
        return "public class Main {}".getBytes(StandardCharsets.UTF_8);
    }
}
