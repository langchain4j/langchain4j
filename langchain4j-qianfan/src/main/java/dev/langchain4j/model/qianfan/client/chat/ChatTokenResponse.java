package dev.langchain4j.model.qianfan.client.chat;

public class ChatTokenResponse {
    private final String refreshToken;
    private final Integer expiresIn;
    private final String sessionKey;
    private final String accessToken;
    private final String scope;
    private final String sessionSecret;
    private ChatTokenResponse(Builder builder) {
        this.refreshToken = builder.refreshToken;
        this.expiresIn = builder.expiresIn;
        this.sessionKey = builder.sessionKey;
        this.accessToken = builder.accessToken;
        this.scope = builder.scope;
        this.sessionSecret = builder.sessionSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getScope() {
        return scope;
    }

    public String getSessionSecret() {
        return sessionSecret;
    }
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String refreshToken;
        private Integer expiresIn;
        private String sessionKey;
        private String accessToken;
        private String scope;
        private String sessionSecret;

        private Builder() {
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder expiresIn(Integer expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder sessionKey(String sessionKey) {
            this.sessionKey = sessionKey;
            return this;
        }
        public Builder sessionSecret(String sessionSecret) {
            this.sessionSecret = sessionSecret;
            return this;
        }
        public ChatTokenResponse build() {
            return new ChatTokenResponse(this);
        }
    }
   
    
}
