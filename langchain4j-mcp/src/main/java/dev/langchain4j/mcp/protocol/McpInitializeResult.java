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
        private final McpImplementation serverInfo;

        public Result(String protocolVersion, Capabilities capabilities, McpImplementation serverInfo) {
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

        public McpImplementation getServerInfo() {
            return serverInfo;
        }
    }

    public static class Capabilities {

        private final Tools tools;

        public Capabilities(Tools tools) {
            this.tools = tools;
        }

        public Tools getTools() {
            return tools;
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Tools {

            private final Boolean listChanged;

            public Tools(Boolean listChanged) {
                this.listChanged = listChanged;
            }

            public Boolean getListChanged() {
                return listChanged;
            }
        }
    }
}
