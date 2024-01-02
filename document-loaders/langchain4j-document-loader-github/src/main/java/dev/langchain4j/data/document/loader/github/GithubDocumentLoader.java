package dev.langchain4j.data.document.loader.github;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GithubDocumentLoader {

    private static final Logger logger = LoggerFactory.getLogger(GithubDocumentLoader.class);

    private String apiUrl = "https://api.github.com";

    private GitHub github;

    public GithubDocumentLoader(String apiUrl, String githubToken, String gitHubTokenOrganization) throws IOException {
        GitHubBuilder gitHubBuilder = new GitHubBuilder();
        if (apiUrl != null) {
            this.apiUrl = apiUrl;
        }
        gitHubBuilder.withEndpoint(this.apiUrl);
        if (githubToken != null) {
            if (gitHubTokenOrganization == null) {
                gitHubBuilder.withOAuthToken(githubToken);
            } else {
                gitHubBuilder.withOAuthToken(githubToken, gitHubTokenOrganization);
            }
        }
        github = gitHubBuilder.build();
    }

    public GithubDocumentLoader(GitHub github) {
        this.github = github;
    }

    public Document loadDocument(String owner, String repo, String branch, String path, DocumentParser parser) throws IOException {
        GHContent content = github
                .getRepository(owner + "/" + repo)
                .getFileContent(path, branch);

        Document document = fromGitHub(parser, content);
        return document;
    }

    public Document loadDocument(String owner, String repo, String branch, DocumentParser parser) throws IOException {
        return loadDocument(owner, repo, branch,"", parser);
    }

    public List<Document> loadDocuments(String owner, String repo, String branch, String path, DocumentParser parser) throws IOException {
        List<Document> documents = new ArrayList<>();
        github
                .getRepository(owner + "/" + repo)
                .getDirectoryContent(path, branch)
                .forEach(ghDirectoryContent -> {
                    GithubDocumentLoader.scanDirectory(ghDirectoryContent, documents, parser);
                });
        return documents;
    }

    public List<Document> loadDocuments(String owner, String repo, String branch, DocumentParser parser) throws IOException {
        return loadDocuments(owner, repo, branch, "", parser);
    }

    private static void scanDirectory(GHContent ghContent, List<Document> documents, DocumentParser parser) {
        if (ghContent.isDirectory()) {
            try {
                ghContent.listDirectoryContent().forEach(ghDirectoryContent -> {
                    GithubDocumentLoader.scanDirectory(ghDirectoryContent, documents, parser);
                });
            } catch (IOException e) {
                logger.error("Failed to load directory from GitHub: {}", ghContent.getHtmlUrl(), e);
            }
        } else {
            Document document = fromGitHub(parser, ghContent);
            if (document != null) {
                documents.add(document);
            }
        }
    }

    private static Document fromGitHub(DocumentParser parser, GHContent content) {
        logger.info("Loading document from GitHub: {}", content.getHtmlUrl());
        try {
            if (content.isFile()) {
                Document document = parser.parse(content.read());
                document.metadata().add("git_url", content.getGitUrl());
                document.metadata().add("download_url", content.getDownloadUrl());
                document.metadata().add("html_url", content.getHtmlUrl());
                document.metadata().add("name", content.getName());
                document.metadata().add("path", content.getPath());
                document.metadata().add("sha", content.getSha());
                document.metadata().add("size", Long.toString(content.getSize()));
                document.metadata().add("type", content.getType());
                document.metadata().add("url", content.getUrl());
                document.metadata().add("encoding", content.getEncoding());
                return document;
            } else {
                logger.debug("Skipping directory: {}", content.getHtmlUrl());
                return null;
            }
        } catch (IOException e) {
            logger.error("Failed to load document from GitHub: {}", content.getHtmlUrl(), e);
            return null;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String apiUrl = "https://api.github.com";

        private String githubToken;

        private String gitHubTokenOrganization;

        private GitHub github;

        public Builder github(GitHub github) {
            this.github = github;
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public Builder githubToken(String githubToken) {
            this.githubToken = githubToken;
            return this;
        }

        public Builder gitHubTokenOrganization(String gitHubTokenOrganization) {
            this.gitHubTokenOrganization = gitHubTokenOrganization;
            return this;
        }

        public GithubDocumentLoader build() throws IOException {
            if (github != null) {
                return new GithubDocumentLoader(github);
            } else {
                return new GithubDocumentLoader(apiUrl, githubToken, gitHubTokenOrganization);
            }
        }
    }
}
