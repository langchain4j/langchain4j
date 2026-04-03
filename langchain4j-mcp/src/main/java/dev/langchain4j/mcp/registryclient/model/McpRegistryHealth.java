package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class McpRegistryHealth {

    @JsonProperty("github_client_id")
    private String githubClientId;

    private String status;

    public String getGithubClientId() {
        return githubClientId;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "McpRegistryHealth{" +
                "githubClientId='" + githubClientId + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
