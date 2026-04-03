package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

public class McpPackage {

    @JsonAlias("file_sha256")
    private String fileSha256;

    private String identifier;

    private String registryBaseUrl;

    @JsonAlias("registry_type")
    private String registryType;

    private String runtimeHint;

    private String version;
    private McpTransport transport;

    private List<McpRuntimeArgument> runtimeArguments;

    private List<McpPackageArgument> packageArguments;

    private List<McpEnvironmentVariable> environmentVariables;

    public String getFileSha256() {
        return fileSha256;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getRegistryBaseUrl() {
        return registryBaseUrl;
    }

    public String getRegistryType() {
        return registryType;
    }

    public String getRuntimeHint() {
        return runtimeHint;
    }

    public String getVersion() {
        return version;
    }

    public McpTransport getTransport() {
        return transport;
    }

    public List<McpRuntimeArgument> getRuntimeArguments() {
        return runtimeArguments;
    }

    public List<McpPackageArgument> getPackageArguments() {
        return packageArguments;
    }

    public List<McpEnvironmentVariable> getEnvironmentVariables() {
        return environmentVariables;
    }

    @Override
    public String toString() {
        return "McpPackage{" +
                "fileSha256='" + fileSha256 + '\'' +
                ", identifier='" + identifier + '\'' +
                ", registryBaseUrl='" + registryBaseUrl + '\'' +
                ", registryType='" + registryType + '\'' +
                ", runtimeHint='" + runtimeHint + '\'' +
                ", version='" + version + '\'' +
                ", transport=" + transport +
                ", runtimeArguments=" + runtimeArguments +
                ", packageArguments=" + packageArguments +
                ", environmentVariables=" + environmentVariables +
                '}';
    }
}
