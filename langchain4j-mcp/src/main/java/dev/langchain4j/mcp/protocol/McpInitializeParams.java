package dev.langchain4j.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.langchain4j.Internal;

@Internal
public class McpInitializeParams {

    private String protocolVersion;
    private Capabilities capabilities;
    private McpImplementation clientInfo;

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(final String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(final Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public McpImplementation getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(final McpImplementation clientInfo) {
        this.clientInfo = clientInfo;
    }

    public static class Capabilities {

        private Roots roots;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Sampling sampling;

        public Roots getRoots() {
            return roots;
        }

        public void setRoots(final Roots roots) {
            this.roots = roots;
        }

        public Sampling getSampling() {
            return sampling;
        }

        public void setSampling(final Sampling sampling) {
            this.sampling = sampling;
        }

        public static class Roots {

            private boolean listChanged;

            public boolean isListChanged() {
                return listChanged;
            }

            public void setListChanged(final boolean listChanged) {
                this.listChanged = listChanged;
            }
        }

        public static class Sampling {}
    }
}
