package dev.langchain4j.data.document.loader.github;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.source.github.GitHubSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubDocumentLoader {

    private static final Logger logger = LoggerFactory.getLogger(GitHubDocumentLoader.class);

    private final GitHub gitHub;

    public GitHubDocumentLoader(String gitHubToken, String gitHubTokenOrganization) {
        this(null, gitHubToken, gitHubTokenOrganization);
    }

    public GitHubDocumentLoader(String apiUrl, String gitHubToken, String gitHubTokenOrganization) {
        GitHubBuilder gitHubBuilder = new GitHubBuilder();
        if (apiUrl != null) {
            gitHubBuilder.withEndpoint(apiUrl);
        }
        if (gitHubToken != null) {
            if (gitHubTokenOrganization == null) {
                gitHubBuilder.withOAuthToken(gitHubToken);
            } else {
                gitHubBuilder.withOAuthToken(gitHubToken, gitHubTokenOrganization);
            }
        }
        try {
            gitHub = gitHubBuilder.build();
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    public GitHubDocumentLoader() {
        try {
            gitHub = new GitHubBuilder().build();
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
    }

    public GitHubDocumentLoader(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    /**
     * Loads a document from a specific file in a GitHub repository using the provided reference (commit ID, branch name, or tag).
     * <p>
     * This method retrieves the contents of a file from a GitHub repository at a specific version (ref),
     * parses it using the provided {@link DocumentParser}, and returns the resulting {@link Document} object.
     * </p>
     *
     * <p><b>Parameters:</b></p>
     * <ul>
     *     <li><b>owner</b> - The GitHub username or organization name that owns the repository. Must not be blank.</li>
     *     <li><b>repo</b> - The name of the GitHub repository. Must not be blank.</li>
     *     <li><b>ref</b> - The Git reference which can be one of the following:
     *         <ul>
     *             <li>A branch name (e.g., {@code main}, {@code develop})</li>
     *             <li>A tag name (e.g., {@code v1.0.0})</li>
     *             <li>A commit SHA (e.g., {@code a3c6e1b...})</li>
     *         </ul>
     *         If {@code null} or blank, GitHub will use the repository’s default branch (usually {@code main} or {@code master}).
     *     </li>
     *     <li><b>path</b> - The relative file path within the repository to the content to be loaded (e.g., {@code docs/README.md}).</li>
     *     <li><b>parser</b> - An implementation of {@link DocumentParser} used to parse the retrieved file content into a {@link Document} object.</li>
     * </ul>
     *
     * <p><b>Returns:</b></p>
     * A {@link Document} parsed from the contents of the file at the specified location and ref in the GitHub repository.
     *
     * <p><b>Throws:</b></p>
     * <ul>
     *     <li>{@link IllegalArgumentException} if the {@code owner} or {@code repo} is blank or null.</li>
     *     <li>{@link RuntimeException} if the GitHub API call fails or the content cannot be retrieved (wraps {@link IOException}).</li>
     * </ul>
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * Document doc = loader.loadDocument("langchain4j", "langchain4j", "main", "pom.xml", new TextDocumentParser());
     * }</pre>
     *
     * @param owner the GitHub repository owner (user or organization)
     * @param repo the name of the GitHub repository
     * @param ref the name of the commit SHA, branch, or tag. If {@code null}, the repository’s default branch is used
     * @param path the relative path to the file in the repository
     * @param parser the parser used to convert the GitHub content into a Document
     * @return the parsed Document object representing the content of the file
     */
    public Document loadDocument(String owner, String repo, String ref, String path, DocumentParser parser) {
        ensureNotBlank(owner, "owner");
        ensureNotBlank(repo, "repo");
        GHContent content = null;
        try {
            content = gitHub.getRepository(owner + "/" + repo).getFileContent(path, ref);
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
        return fromGitHub(parser, content);
    }

    /**
     * Loads and parses multiple documents from a directory in a GitHub repository at a specific branch.
     * <p>
     * This method recursively scans the specified directory in a GitHub repository at a given branch,
     * retrieves all files contained within (including nested directories), parses each file using the provided
     * {@link DocumentParser}, and returns a list of {@link Document} objects.
     * </p>
     *
     * <p><b>Parameters:</b></p>
     * <ul>
     *     <li><b>owner</b> - The GitHub username or organization name that owns the repository. Must not be blank.</li>
     *     <li><b>repo</b> - The name of the GitHub repository. Must not be blank.</li>
     *     <li><b>branch</b> - The name of the Git branch from which to read the directory contents (e.g., {@code main}, {@code develop}).</li>
     *     <li><b>path</b> - The relative path to the directory within the repository to scan (e.g., {@code docs/} or {@code src/resources/}).</li>
     *     <li><b>parser</b> - An implementation of {@link DocumentParser} used to convert file contents into {@link Document} objects.</li>
     * </ul>
     *
     * <p><b>Returns:</b></p>
     * A list of {@link Document} objects parsed from the files found in the specified directory and its subdirectories.
     *
     * <p><b>Throws:</b></p>
     * <ul>
     *     <li>{@link IllegalArgumentException} if {@code owner} or {@code repo} is blank or null.</li>
     *     <li>{@link RuntimeException} if an {@link IOException} occurs while accessing the GitHub repository content.</li>
     * </ul>
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * List<Document> docs = loader.loadDocuments(
     *     "langchain4j",
     *     "langchain4j",
     *     "main",
     *     "docs/",
     *     new MarkdownParser()
     * );
     * }</pre>
     *
     * @param owner the GitHub repository owner (user or organization)
     * @param repo the name of the GitHub repository
     * @param branch the name of the Git branch to fetch the directory contents from
     * @param path the relative path to the directory in the repository
     * @param parser the parser used to convert each file into a Document
     * @return a list of parsed Document objects from the specified directory
     */
    public List<Document> loadDocuments(String owner, String repo, String branch, String path, DocumentParser parser) {
        ensureNotBlank(owner, "owner");
        ensureNotBlank(repo, "repo");
        List<Document> documents = new ArrayList<>();
        try {
            gitHub.getRepository(owner + "/" + repo)
                    .getDirectoryContent(path, branch)
                    .forEach(ghDirectoryContent ->
                            GitHubDocumentLoader.scanDirectory(ghDirectoryContent, documents, parser));
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
        return documents;
    }

    public List<Document> loadDocuments(String owner, String repo, String branch, DocumentParser parser) {
        return loadDocuments(owner, repo, branch, "", parser);
    }

    private static void scanDirectory(GHContent ghContent, List<Document> documents, DocumentParser parser) {
        if (ghContent.isDirectory()) {
            try {
                ghContent
                        .listDirectoryContent()
                        .forEach(ghDirectoryContent ->
                                GitHubDocumentLoader.scanDirectory(ghDirectoryContent, documents, parser));
            } catch (IOException ioException) {
                logger.error("Failed to read directory from GitHub: {}", ghContent.getHtmlUrl(), ioException);
            }
        } else {
            Document document = null;
            try {
                document = withRetry(() -> fromGitHub(parser, ghContent), 2);
            } catch (RuntimeException runtimeException) {
                logger.error("Failed to read document from GitHub: {}", ghContent.getHtmlUrl(), runtimeException);
            }
            if (document != null) {
                documents.add(document);
            }
        }
    }

    private static Document fromGitHub(DocumentParser parser, GHContent content) {
        logger.info("Loading document from GitHub: {}", content.getHtmlUrl());
        try {
            if (content.isFile()) {
                GitHubSource source = new GitHubSource(content);
                return DocumentLoader.load(source, parser);
            } else {
                throw new IllegalArgumentException(
                        "Content must be a file, and not a directory: " + content.getHtmlUrl());
            }
        } catch (IOException ioException) {
            throw new RuntimeException("Failed to load document from GitHub: {}", ioException);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String apiUrl;

        private String gitHubToken;

        private String gitHubTokenOrganization;

        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public Builder gitHubToken(String gitHubToken) {
            this.gitHubToken = gitHubToken;
            return this;
        }

        public Builder gitHubTokenOrganization(String gitHubTokenOrganization) {
            this.gitHubTokenOrganization = gitHubTokenOrganization;
            return this;
        }

        public GitHubDocumentLoader build() {
            return new GitHubDocumentLoader(apiUrl, gitHubToken, gitHubTokenOrganization);
        }
    }
}
