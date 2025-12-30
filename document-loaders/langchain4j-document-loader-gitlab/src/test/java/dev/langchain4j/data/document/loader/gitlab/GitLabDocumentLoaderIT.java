package dev.langchain4j.data.document.loader.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GITLAB_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GITLAB_PROJECT_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "GITLAB_TEST_FILE", matches = ".+")
class GitLabDocumentLoaderIT {

    private static final String ENV_GITLAB_TOKEN = "GITLAB_TOKEN";
    private static final String ENV_GITLAB_PROJECT_ID = "GITLAB_PROJECT_ID";
    private static final String ENV_GITLAB_TEST_FILE = "GITLAB_TEST_FILE";
    private static final String ENV_GITLAB_BASE_URL = "GITLAB_BASE_URL";
    private static final String ENV_GITLAB_REF = "GITLAB_REF";

    private static final String DEFAULT_BASE_URL = "https://gitlab.com";

    private GitLabDocumentLoader loader;
    private final DocumentParser parser = new TextDocumentParser();

    @BeforeEach
    void setUp() {
        loader = GitLabDocumentLoader.builder()
                .baseUrl(envOrDefault(ENV_GITLAB_BASE_URL, DEFAULT_BASE_URL))
                .projectId(envRequired(ENV_GITLAB_PROJECT_ID))
                .personalAccessToken(envRequired(ENV_GITLAB_TOKEN))
                .build();
    }

    @Test
    void should_load_file() {
        String ref = blankToNull(System.getenv(ENV_GITLAB_REF));
        String filePath = envRequired(ENV_GITLAB_TEST_FILE);

        Document document = loader.loadDocument(ref, filePath, parser);

        assertThat(document.text()).isNotBlank();
        assertThat(document.metadata().getString(GitLabDocumentLoader.METADATA_GITLAB_PROJECT_ID))
                .isEqualTo(envRequired(ENV_GITLAB_PROJECT_ID));
        assertThat(document.metadata().getString(Document.FILE_NAME)).isEqualTo(fileName(filePath));
        assertThat(document.metadata().getString(Document.URL)).contains("/-/blob/");
        if (!isBlank(ref)) {
            assertThat(document.metadata().getString(Document.URL)).contains("/-/blob/" + ref + "/");
        }
    }

    @Test
    void should_load_documents_from_directory() {
        String ref = blankToNull(System.getenv(ENV_GITLAB_REF));
        String filePath = envRequired(ENV_GITLAB_TEST_FILE);
        String directory = directoryOf(filePath);

        List<Document> documents = loader.loadDocuments(ref, directory, false, parser);

        assertThat(documents).isNotEmpty();
        boolean hasTargetFile = documents.stream()
                .map(doc -> doc.metadata().getString(Document.FILE_NAME))
                .anyMatch(fileName(filePath)::equals);
        assertThat(hasTargetFile).isTrue();
    }

    @Test
    void should_fail_when_project_does_not_exist() {
        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl(envOrDefault(ENV_GITLAB_BASE_URL, DEFAULT_BASE_URL))
                .projectId("non-existent-project-" + System.currentTimeMillis())
                .personalAccessToken(envRequired(ENV_GITLAB_TOKEN))
                .build();

        assertThatThrownBy(() -> loader.loadDocuments(parser))
                .isInstanceOf(RuntimeException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("status code 404"));
    }

    @Test
    void should_fail_with_invalid_token() {
        GitLabDocumentLoader loader = GitLabDocumentLoader.builder()
                .baseUrl(envOrDefault(ENV_GITLAB_BASE_URL, DEFAULT_BASE_URL))
                .projectId(envRequired(ENV_GITLAB_PROJECT_ID))
                .personalAccessToken("invalid-token")
                .build();

        assertThatThrownBy(() -> loader.loadDocuments(parser))
                .isInstanceOf(RuntimeException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("status code 401"));
    }

    private static String envRequired(String name) {
        return System.getenv(name);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalizePath(String path) {
        String normalized = path == null ? "" : path.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String fileName(String path) {
        String normalized = normalizePath(path);
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    private static String directoryOf(String path) {
        String normalized = normalizePath(path);
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(0, idx) : null;
    }
}
