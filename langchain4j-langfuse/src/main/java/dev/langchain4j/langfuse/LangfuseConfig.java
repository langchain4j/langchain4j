package dev.langchain4j.langfuse;

public class LangfuseConfig {

    private final String publicKey;
    private final String secretKey;
    private final String baseUrl;
    private final int maxContentLength;
    private final int batchSize;
    private final long flushInterval;
    private final boolean tracingEnabled;

    public static Builder builder() {
        return new Builder();
    }

    private LangfuseConfig(Builder builder) {
        this.publicKey = builder.publicKey;
        this.secretKey = builder.secretKey;
        this.baseUrl = builder.baseUrl;
        this.tracingEnabled = builder.tracingEnabled;
        this.maxContentLength = builder.maxContentLength;
        this.batchSize = builder.batchSize;
        this.flushInterval = builder.flushInterval;
    }

    public static class Builder {
        private String publicKey;
        private String secretKey;
        private String baseUrl = "https://cloud.langfuse.com";
        private int maxContentLength = 1000;
        private int batchSize = 20;
        private long flushInterval = 5000;
        private boolean tracingEnabled = false;

        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder tracingEnabled(boolean tracingEnabled) {
            this.tracingEnabled = tracingEnabled;
            return this;
        }

        public Builder maxContentLength(int maxContentLength) {
            this.maxContentLength = maxContentLength;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder flushInterval(long flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }

        public LangfuseConfig build() {
            if (tracingEnabled) {
                if (publicKey == null || publicKey.isEmpty()) {
                    throw new IllegalArgumentException("Public key must be provided when Langfuse tracing is enabled.");
                }
                if (secretKey == null || secretKey.isEmpty()) {
                    throw new IllegalArgumentException("Secret key must be provided when Langfuse tracing is enabled.");
                }
            }
            return new LangfuseConfig(this);
        }
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getFlushInterval() {
        return flushInterval;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    @Override
    public String toString() {
        return "LangfuseConfig{"
                + "publicKey='****'"
                + ", secretKey='****'"
                + ", baseUrl='"
                + baseUrl
                + '\''
                + ", tracingEnabled="
                + tracingEnabled
                + '}';
    }
}
