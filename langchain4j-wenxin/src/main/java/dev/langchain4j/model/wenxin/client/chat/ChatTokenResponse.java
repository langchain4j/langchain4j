package dev.langchain4j.model.wenxin.client.chat;

public class ChatTokenResponse {
    private final String refresh_token;
    private final Integer expires_in;
    private final String session_key;
    private final String access_token;
    private final String scope;
    private final String session_secret;
    private ChatTokenResponse(Builder builder) {
        this.refresh_token = builder.refresh_token;
        this.expires_in = builder.expires_in;
        this.session_key = builder.session_key;
        this.access_token = builder.access_token;
        this.scope = builder.scope;
        this.session_secret = builder.session_secret;
    }

    public String getRefresh_token() {
        return refresh_token;
    }

    public Integer getExpires_in() {
        return expires_in;
    }

    public String getSession_key() {
        return session_key;
    }

    public String getAccess_token() {
        return access_token;
    }

    public String getScope() {
        return scope;
    }

    public String getSession_secret() {
        return session_secret;
    }
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String refresh_token;
        private Integer expires_in;
        private String session_key;
        private String access_token;
        private String scope;
        private String session_secret;

        private Builder() {
        }

        public Builder refresh_token(String refresh_token) {
            this.refresh_token = refresh_token;
            return this;
        }

        public Builder expires_in(Integer expires_in) {
            this.expires_in = expires_in;
            return this;
        }

        public Builder access_token(String access_token) {
            this.access_token = access_token;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder session_key(String session_key) {
            this.session_key = session_key;
            return this;
        }
        public Builder session_secret(String session_secret) {
            this.session_secret = session_secret;
            return this;
        }
        public ChatTokenResponse build() {
            return new ChatTokenResponse(this);
        }
    }
   
    
}
