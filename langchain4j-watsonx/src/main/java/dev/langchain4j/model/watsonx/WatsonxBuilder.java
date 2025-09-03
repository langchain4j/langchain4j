package dev.langchain4j.model.watsonx;

import com.ibm.watsonx.ai.core.auth.AuthenticationProvider;
import java.net.URI;

@SuppressWarnings("unchecked")
abstract class WatsonxBuilder<T extends WatsonxBuilder<T>> {

    protected URI url;
    protected String version;
    protected String apiKey;
    protected Boolean logRequests;
    protected Boolean logResponses;
    protected AuthenticationProvider authenticationProvider;

    public T url(String url) {
        return url(URI.create(url));
    }

    public T url(URI url) {
        this.url = url;
        return (T) this;
    }

    public T version(String version) {
        this.version = version;
        return (T) this;
    }

    public T apiKey(String apiKey) {
        this.apiKey = apiKey;
        return (T) this;
    }

    public T logRequests(Boolean logRequests) {
        this.logRequests = logRequests;
        return (T) this;
    }

    public T logResponses(Boolean logResponses) {
        this.logResponses = logResponses;
        return (T) this;
    }

    public T authenticationProvider(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
        return (T) this;
    }
}
