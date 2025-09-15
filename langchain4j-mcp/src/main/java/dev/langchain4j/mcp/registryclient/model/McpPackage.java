package dev.langchain4j.mcp.registryclient.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class McpPackage {

    @JsonProperty("file_sha256")
    private String fileSha256;

    private String identifier;

    @JsonProperty("registry_base_url")
    private String registryBaseUrl;

    @JsonProperty("registry_type")
    private String registryType;

    @JsonProperty("runtime_hint")
    private String runtimeHint;

    private String version;
    private McpTransport transport;

    @JsonProperty("runtime_arguments")
    private List<McpRuntimeArgument> runtimeArguments;

    @JsonProperty("package_arguments")
    private List<McpPackageArgument> packageArguments;

    @JsonProperty("environment_variables")
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
}
