package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import org.jspecify.annotations.Nullable;

/**
 * Corresponds to the {@code InitializeResult} type from the MCP schema.
 */
@Internal
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpInitializeResult extends McpJsonRpcMessage {

    private final Result result;

    @JsonCreator
    public McpInitializeResult(@JsonProperty("id") Long id, @JsonProperty("result") Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        private final String protocolVersion;
        private final Capabilities capabilities;
        private final McpImplementation serverInfo;
        private final @Nullable String instructions;

        @JsonCreator
        public Result(@JsonProperty("protocolVersion") String protocolVersion, @JsonProperty("capabilities") Capabilities capabilities, @JsonProperty("serverInfo") McpImplementation serverInfo) {
            this(protocolVersion, capabilities, serverInfo, null);
        }

        public Result(
                String protocolVersion,
                Capabilities capabilities,
                McpImplementation serverInfo,
                @Nullable String instructions) {
            this.protocolVersion = protocolVersion;
            this.capabilities = capabilities;
            this.serverInfo = serverInfo;
            this.instructions = instructions;
        }

        public String getProtocolVersion() {
            return protocolVersion;
        }

        public Capabilities getCapabilities() {
            return capabilities;
        }

        public McpImplementation getServerInfo() {
            return serverInfo;
        }

        public @Nullable String getInstructions() {
            return instructions;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)

    public static class Capabilities {

        private final Tools tools;

        @JsonCreator
            public Capabilities(@JsonProperty("tools") Tools tools) {
            this.tools = tools;
        }

        public Tools getTools() {
            return tools;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Tools {

            private final Boolean listChanged;

            @JsonCreator
            public Tools(@JsonProperty("listChanged") Boolean listChanged) {
                this.listChanged = listChanged;
            }

            public Boolean getListChanged() {
                return listChanged;
            }
        }
    }
}
