package dev.langchain4j.data.document.loader.github;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.source.github.GitHubSource;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.model.chat.policy.RetryUtils.withRetry;

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

    public Document loadDocument(String owner, String repo, String branch, String path, DocumentParser parser) {
        GHContent content = null;
        try {
            content = gitHub
                    .getRepository(owner + "/" + repo)
                    .getFileContent(path, branch);
        } catch (IOException ioException) {
            throw new RuntimeException(ioException);
        }
        return fromGitHub(parser, content);
    }

    public List<Document> loadDocuments(String owner, String repo, String branch, String path, DocumentParser parser) {
        List<Document> documents = new ArrayList<>();
        try {
            gitHub
                    .getRepository(owner + "/" + repo)
                    .getDirectoryContent(path, branch)
                    .forEach(ghDirectoryContent -> GitHubDocumentLoader.scanDirectory(ghDirectoryContent, documents, parser));
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
                ghContent.listDirectoryContent().forEach(ghDirectoryContent -> GitHubDocumentLoader.scanDirectory(ghDirectoryContent, documents, parser));
            } catch (IOException ioException) {
                logger.error("Failed to read directory from GitHub: {}", ghContent.getHtmlUrl(), ioException);
            }
        } else {
            Document document = null;
            try {
                document = withRetry(() -> fromGitHub(parser, ghContent), 3);
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
                throw new IllegalArgumentException("Content must be a file, and not a directory: " + content.getHtmlUrl());
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
