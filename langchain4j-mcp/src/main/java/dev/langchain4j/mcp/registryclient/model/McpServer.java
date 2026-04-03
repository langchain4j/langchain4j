package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class McpServer {

    private String name;
    private String description;

    @JsonProperty("$schema")
    private String schema;

    private String status;
    private McpRepository repository;
    private String version;

    @JsonAlias("website_url")
    private String websiteUrl;

    private List<McpRemote> remotes;

    @JsonProperty("_meta")
    private McpMeta meta;

    private List<McpPackage> packages;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSchema() {
        return schema;
    }

    /**
     * @deprecated This field was moved to the McpOfficialMeta object in schema version 2025-09-29
     */
    @Deprecated(forRemoval = true)
    public String getStatus() {
        return status;
    }

    public McpRepository getRepository() {
        return repository;
    }

    public String getVersion() {
        return version;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public List<McpRemote> getRemotes() {
        return remotes;
    }

    public McpMeta getMeta() {
        return meta;
    }

    public List<McpPackage> getPackages() {
        return packages;
    }

    @Override
    public String toString() {
        return "McpServer{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", schema='" + schema + '\'' +
                ", status='" + status + '\'' +
                ", repository=" + repository +
                ", version='" + version + '\'' +
                ", websiteUrl='" + websiteUrl + '\'' +
                ", remotes=" + remotes +
                ", meta=" + meta +
                ", packages=" + packages +
                '}';
    }
}
