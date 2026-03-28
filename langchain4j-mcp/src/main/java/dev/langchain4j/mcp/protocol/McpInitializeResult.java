package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;

@Internal
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

        @JsonCreator
        public Result(
                @JsonProperty("protocolVersion") String protocolVersion,
                @JsonProperty("capabilities") Capabilities capabilities,
                @JsonProperty("serverInfo") McpImplementation serverInfo) {
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Capabilities {

        private Logging logging;
        private Prompts prompts;
        private Resources resources;
        private Tools tools;

        private Capabilities() {}

        public Logging getLogging() {
            return logging;
        }

        public Prompts getPrompts() {
            return prompts;
        }

        public Resources getResources() {
            return resources;
        }

        public Tools getTools() {
            return tools;
        }

        // ---------- Sub-capabilities ----------

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Logging {
            // marker capability
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Prompts {
            private Boolean listChanged;

            public Prompts() {}

            public Prompts(Boolean listChanged) {
                this.listChanged = listChanged;
            }

            public Boolean getListChanged() {
                return listChanged;
            }
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Resources {
            private Boolean subscribe;
            private Boolean listChanged;

            public Resources() {}

            public Resources(Boolean subscribe, Boolean listChanged) {
                this.subscribe = subscribe;
                this.listChanged = listChanged;
            }

            public Boolean getSubscribe() {
                return subscribe;
            }

            public Boolean getListChanged() {
                return listChanged;
            }
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Tools {
            private Boolean listChanged;

            public Tools() {}

            public Tools(Boolean listChanged) {
                this.listChanged = listChanged;
            }

            public Boolean getListChanged() {
                return listChanged;
            }
        }

        // ---------- Builder ----------

        public static class Builder {

            private final Capabilities capabilities;

            public Builder() {
                this.capabilities = new Capabilities();
            }

            public Builder logging() {
                capabilities.logging = new Logging();
                return this;
            }

            public Builder prompts(Boolean listChanged) {
                capabilities.prompts = new Prompts(listChanged);
                return this;
            }

            public Builder resources(Boolean subscribe, Boolean listChanged) {
                capabilities.resources = new Resources(subscribe, listChanged);
                return this;
            }

            public Builder tools(Boolean listChanged) {
                capabilities.tools = new Tools(listChanged);
                return this;
            }

            public Capabilities build() {
                return capabilities;
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }
}
