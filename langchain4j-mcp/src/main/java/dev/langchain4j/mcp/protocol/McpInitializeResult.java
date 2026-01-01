package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpInitializeResult extends McpJsonRpcMessage {

    private final Result result;

    public McpInitializeResult(Long id, Result result) {
        super(id);
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {

        private final String protocolVersion;
        private final Capabilities capabilities;
        private final ServerInfo serverInfo;

        public Result(String protocolVersion, Capabilities capabilities, ServerInfo serverInfo) {
            this.protocolVersion = protocolVersion;
            this.capabilities = capabilities;
            this.serverInfo = serverInfo;
        }

        public String getProtocolVersion() {
            return protocolVersion;
        }

        public Capabilities getCapabilities() {
            return capabilities;
        }

        public ServerInfo getServerInfo() {
            return serverInfo;
        }
    }

    public static class Capabilities {

        private final Boolean tools;

        public Capabilities(Boolean tools) {
            this.tools = tools;
        }

        public Boolean getTools() {
            return tools;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ServerInfo {

        private final String name;
        private final String version;

        public ServerInfo(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }
    }
}
