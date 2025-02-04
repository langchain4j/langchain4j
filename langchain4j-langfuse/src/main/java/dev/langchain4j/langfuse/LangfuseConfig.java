package dev.langchain4j.langfuse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LangfuseConfig {

    private final String publicKey;
    private final String secretKey;
    private final String endpoint;
    private final int maxContentLength;
    private final int batchSize;
    private final long flushInterval;
    private final boolean enabled;

    public static Builder builder() {
        return new Builder();
    }

    private LangfuseConfig(Builder builder) {
        this.publicKey = builder.publicKey;
        this.secretKey = builder.secretKey;
        this.endpoint = builder.endpoint;
        this.enabled = builder.enabled;
        this.maxContentLength = builder.maxContentLength;
        this.batchSize = builder.batchSize;
        this.flushInterval = builder.flushInterval;
    }

    public static class Builder {
        private String publicKey;
        private String secretKey;
        private String endpoint = "https://cloud.langfuse.com";
        private int maxContentLength = 1000;
        private int batchSize = 20;
        private long flushInterval = 5000;
        private boolean enabled = false;

        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
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
            if (enabled) {
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

    public static LangfuseConfig fromEnv() {
        String publicKey = System.getenv("LANGFUSE_PUBLIC_KEY");
        String secretKey = System.getenv("LANGFUSE_SECRET_KEY");
        String endpoint = System.getenv("LANGFUSE_ENDPOINT");
        boolean enabled = Boolean.parseBoolean(System.getenv("LANGFUSE_ENABLED"));

        return LangfuseConfig.builder()
                .publicKey(publicKey)
                .secretKey(secretKey)
                .endpoint(endpoint != null ? endpoint : "https://cloud.langfuse.com")
                .enabled(enabled)
                .build();
    }

    public static LangfuseConfig fromProperties(String propertiesFileName) {
        Properties props = loadProperties(propertiesFileName);

        String publicKey = props.getProperty("langfuse.public.key");
        String secretKey = props.getProperty("langfuse.secret.key");
        String endpoint = props.getProperty("langfuse.endpoint");
        boolean enabled = Boolean.parseBoolean(props.getProperty("langfuse.enabled", "false"));
        int maxContentLength = Integer.parseInt(props.getProperty("langfuse.max.content.length", "1000"));
        int batchSize = Integer.parseInt(props.getProperty("langfuse.batch.size", "20"));
        long flushInterval = Long.parseLong(props.getProperty("langfuse.flush.interval", "5000"));

        return LangfuseConfig.builder()
                .publicKey(publicKey)
                .secretKey(secretKey)
                .endpoint(endpoint != null ? endpoint : "https://cloud.langfuse.com")
                .enabled(enabled)
                .maxContentLength(maxContentLength)
                .batchSize(batchSize)
                .flushInterval(flushInterval)
                .build();
    }

    private static Properties loadProperties(String propertiesFileName) {
        Properties props = new Properties();
        try (InputStream input = LangfuseConfig.class.getClassLoader().getResourceAsStream(propertiesFileName)) {
            if (input == null) {
                throw new IllegalArgumentException("Unable to find " + propertiesFileName + " in classpath");
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load properties file: " + propertiesFileName, e);
        }
        return props;
    }

    public static LangfuseConfig defaultConfigFromProperties() {
        return fromProperties("langfuse.properties");
    }

    public static LangfuseConfig defaultConfigFromEnv() {
        return fromEnv();
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public long getFlushInterval() {
        return flushInterval;
    }

    @Override
    public String toString() {
        return "LangfuseConfig{"
                + "publicKey='****'"
                + ", secretKey='****'"
                + ", endpoint='"
                + endpoint
                + '\''
                + ", enabled="
                + enabled
                + '}';
    }
}
