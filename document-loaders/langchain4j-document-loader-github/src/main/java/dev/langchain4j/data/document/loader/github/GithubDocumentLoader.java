package dev.langchain4j.data.document.loader.github;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class GithubDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(GithubDocumentLoader.class);

    private String baseUrl = "https://github.com";

    private String apiUrl = "https://api.github.com";

    private String githubToken;

    private GitHub github;

    public GithubDocumentLoader() {

    }

    public Document loadDocument(String owner, String repo, String branch, String path, DocumentParser parser) throws IOException {
        GitHub github = new GitHubBuilder().build();
        GHContent content = github
                .getRepository(owner + "/" + repo)
                .getFileContent(path, branch);

        Document document = parser.parse(content.read());
        document.metadata().add("git_url", content.getGitUrl());
        return document;
    }

    public Document loadDocument(String owner, String repo, String branch, DocumentParser parser) throws IOException {
        return loadDocument(owner, repo, branch,"", parser);
    }

    public List<Document> loadDocuments(String owner, String repo, String branch, String path, DocumentParser parser) {
        return null;
    }

    public List<Document> loadDocuments(String owner, String repo, String branch, DocumentParser parser) {
        return loadDocuments(owner, repo, branch, "", parser);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl = "https://github.com";

        private String apiUrl = "https://api.github.com";

        private String githubToken;

        private GitHub github;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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

        public GithubDocumentLoader build() {
            return new GithubDocumentLoader();
        }
    }
}
