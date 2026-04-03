package dev.langchain4j.model.watsonx;

import com.ibm.watsonx.ai.CloudRegion;
import com.ibm.watsonx.ai.core.auth.Authenticator;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@SuppressWarnings("unchecked")
abstract class WatsonxBuilder<T extends WatsonxBuilder<T>> {

    protected URI baseUrl;
    protected String version;
    protected String apiKey;
    protected String projectId;
    protected String spaceId;
    protected Boolean logRequests;
    protected Boolean logResponses;
    protected Duration timeout;
    protected Authenticator authenticator;
    protected HttpClient httpClient;
    protected boolean verifySsl = true;

    public T baseUrl(CloudRegion baseUrl) {
        return baseUrl(baseUrl.mlEndpoint());
    }

    public T baseUrl(String url) {
        return baseUrl(URI.create(url));
    }

    public T baseUrl(URI url) {
        this.baseUrl = url;
        return (T) this;
    }

    public T version(String version) {
        this.version = version;
        return (T) this;
    }

    public T projectId(String projectId) {
        this.projectId = projectId;
        return (T) this;
    }

    public T spaceId(String spaceId) {
        this.spaceId = spaceId;
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

    public T timeout(Duration timeout) {
        this.timeout = timeout;
        return (T) this;
    }

    public T authenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
        return (T) this;
    }

    public T httpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        return (T) this;
    }

    public T verifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
        return (T) this;
    }
}
