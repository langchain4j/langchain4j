package dev.langchain4j.data.document.loader.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GitLabDocumentLoaderTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldLoadTwoDocumentsFromRepositoryTree() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);

        HttpResponse<String> treeResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(treeResponse.statusCode()).thenReturn(200);
        when(treeResponse.body()).thenReturn(repositoryTreeResponseBody());
        when(treeResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

        HttpResponse<byte[]> readmeResponse = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(readmeResponse.statusCode()).thenReturn(200);
        when(readmeResponse.body()).thenReturn(readmeRawContent());

        HttpResponse<byte[]> mainJavaResponse = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(mainJavaResponse.statusCode()).thenReturn(200);
        when(mainJavaResponse.body()).thenReturn(mainJavaRawContent());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn((HttpResponse) treeResponse, (HttpResponse) readmeResponse, (HttpResponse) mainJavaResponse);

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
        assertThat(readme.metadata().getString(GitLabDocumentLoader.METADATA_GITLAB_PROJECT_ID)).isEqualTo("123");
        assertThat(readme.metadata().getString(Document.FILE_NAME)).isEqualTo("README.md");
        assertThat(readme.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.com/projects/123/-/blob/main/README.md");

        Document mainJava = documents.get(1);
        assertThat(mainJava.text()).isEqualTo("public class Main {}");
        assertThat(mainJava.metadata().getString(GitLabDocumentLoader.METADATA_GITLAB_PROJECT_ID)).isEqualTo("123");
        assertThat(mainJava.metadata().getString(Document.FILE_NAME)).isEqualTo("Main.java");
        assertThat(mainJava.metadata().getString(Document.URL))
                .isEqualTo("https://gitlab.com/projects/123/-/blob/main/src/Main.java");

        List<HttpRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).hasSize(3);

        assertThat(requests.get(0).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=1&ref=main");
        assertThat(requests.get(1).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/README.md/raw?ref=main");
        assertThat(requests.get(2).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/src%2FMain.java/raw?ref=main");

        assertThat(requests.get(0).headers().firstValue("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(1).headers().firstValue("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(2).headers().firstValue("PRIVATE-TOKEN")).contains("token");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldHandlePaginationWhenListingRepositoryTree() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);

        HttpResponse<String> treePage1Response = (HttpResponse<String>) mock(HttpResponse.class);
        when(treePage1Response.statusCode()).thenReturn(200);
        when(treePage1Response.body()).thenReturn(repositoryTreeResponseBodyPage1());
        when(treePage1Response.headers())
                .thenReturn(HttpHeaders.of(Map.of("X-Next-Page", List.of("2")), (k, v) -> true));

        HttpResponse<String> treePage2Response = (HttpResponse<String>) mock(HttpResponse.class);
        when(treePage2Response.statusCode()).thenReturn(200);
        when(treePage2Response.body()).thenReturn(repositoryTreeResponseBodyPage2());
        when(treePage2Response.headers())
                .thenReturn(HttpHeaders.of(Map.of("X-Next-Page", List.of("")), (k, v) -> true));

        HttpResponse<byte[]> readmeResponse = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(readmeResponse.statusCode()).thenReturn(200);
        when(readmeResponse.body()).thenReturn(readmeRawContent());

        HttpResponse<byte[]> mainJavaResponse = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(mainJavaResponse.statusCode()).thenReturn(200);
        when(mainJavaResponse.body()).thenReturn(mainJavaRawContent());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(
                        (HttpResponse) treePage1Response,
                        (HttpResponse) treePage2Response,
                        (HttpResponse) readmeResponse,
                        (HttpResponse) mainJavaResponse);

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

        assertThat(requests.get(0).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=1&ref=main");
        assertThat(requests.get(1).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=2&ref=main");
        assertThat(requests.get(2).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/README.md/raw?ref=main");
        assertThat(requests.get(3).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/src%2FMain.java/raw?ref=main");

        assertThat(requests.get(0).headers().firstValue("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(1).headers().firstValue("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(2).headers().firstValue("PRIVATE-TOKEN")).contains("token");
        assertThat(requests.get(3).headers().firstValue("PRIVATE-TOKEN")).contains("token");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldFallbackFromMainToMasterWhenBranchNotFound() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);

        HttpResponse<String> treeMainNotFoundResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(treeMainNotFoundResponse.statusCode()).thenReturn(404);
        when(treeMainNotFoundResponse.body()).thenReturn("Not Found");

        HttpResponse<String> treeMasterResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(treeMasterResponse.statusCode()).thenReturn(200);
        when(treeMasterResponse.body()).thenReturn(repositoryTreeResponseBody());
        when(treeMasterResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

        HttpResponse<byte[]> readmeResponse = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(readmeResponse.statusCode()).thenReturn(200);
        when(readmeResponse.body()).thenReturn(readmeRawContent());

        HttpResponse<byte[]> mainJavaResponse = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(mainJavaResponse.statusCode()).thenReturn(200);
        when(mainJavaResponse.body()).thenReturn(mainJavaRawContent());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(
                        (HttpResponse) treeMainNotFoundResponse,
                        (HttpResponse) treeMasterResponse,
                        (HttpResponse) readmeResponse,
                        (HttpResponse) mainJavaResponse);

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

        assertThat(requests.get(0).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=1&ref=main");
        assertThat(requests.get(1).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/tree?recursive=true&per_page=100&page=1&ref=master");
        assertThat(requests.get(2).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/README.md/raw?ref=master");
        assertThat(requests.get(3).uri().toString())
                .isEqualTo("https://gitlab.com/api/v4/projects/123/repository/files/src%2FMain.java/raw?ref=master");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldBuildUrlsForProjectPath() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);

        HttpResponse<String> treeResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(treeResponse.statusCode()).thenReturn(200);
        when(treeResponse.body()).thenReturn(repositoryTreeResponseBodyReadmeOnly());
        when(treeResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

        HttpResponse<byte[]> readmeResponse = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(readmeResponse.statusCode()).thenReturn(200);
        when(readmeResponse.body()).thenReturn(readmeRawContent());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn((HttpResponse) treeResponse, (HttpResponse) readmeResponse);

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

        assertThat(requests.get(0).uri().toString())
                .isEqualTo(
                        "https://gitlab.example.com/api/v4/projects/group%2Fproject/repository/tree?recursive=true&per_page=100&page=1&ref=main");
        assertThat(requests.get(1).uri().toString())
                .isEqualTo("https://gitlab.example.com/api/v4/projects/group%2Fproject/repository/files/README.md/raw?ref=main");
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

    private static byte[] readmeRawContent() {
        return "README content".getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] mainJavaRawContent() {
        return "public class Main {}".getBytes(StandardCharsets.UTF_8);
    }
}
