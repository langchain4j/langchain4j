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

    /**
     * Sets the IBM watsonx.ai endpoint from a predefined {@link CloudRegion}.
     *
     * @param baseUrl the IBM Cloud region whose ML endpoint will be used
     * @return {@code this}
     */
    public T baseUrl(CloudRegion baseUrl) {
        return baseUrl(baseUrl.mlEndpoint());
    }

    /**
     * Sets the base URL of the IBM watsonx.ai API.
     *
     * @param url the base URL string, e.g. {@code "https://us-south.ml.cloud.ibm.com"}
     * @return {@code this}
     */
    public T baseUrl(String url) {
        return baseUrl(URI.create(url));
    }

    /**
     * Sets the base URL of the IBM watsonx.ai API as a {@link URI}.
     *
     * @param url the base URL URI
     * @return {@code this}
     */
    public T baseUrl(URI url) {
        this.baseUrl = url;
        return (T) this;
    }

    /**
     * Sets the watsonx.ai API version date, e.g. {@code "2024-05-31"}.
     *
     * @param version the API version date string
     * @return {@code this}
     */
    public T version(String version) {
        this.version = version;
        return (T) this;
    }

    /**
     * Sets the IBM Cloud project ID that owns the watsonx.ai resources.
     * Exactly one of {@code projectId} or {@code spaceId} must be set.
     *
     * @param projectId the IBM Cloud project ID
     * @return {@code this}
     */
    public T projectId(String projectId) {
        this.projectId = projectId;
        return (T) this;
    }

    /**
     * Sets the IBM Cloud deployment space ID.
     * Exactly one of {@code projectId} or {@code spaceId} must be set.
     *
     * @param spaceId the IBM Cloud deployment space ID
     * @return {@code this}
     */
    public T spaceId(String spaceId) {
        this.spaceId = spaceId;
        return (T) this;
    }

    /**
     * Sets the IBM Cloud API key used to generate IAM access tokens for authentication.
     *
     * @param apiKey the IBM Cloud API key
     * @return {@code this}
     */
    public T apiKey(String apiKey) {
        this.apiKey = apiKey;
        return (T) this;
    }

    /**
     * Enables debug logging of request bodies sent to the watsonx.ai API.
     *
     * @param logRequests {@code true} to enable request logging
     * @return {@code this}
     */
    public T logRequests(Boolean logRequests) {
        this.logRequests = logRequests;
        return (T) this;
    }

    /**
     * Enables debug logging of response bodies received from the watsonx.ai API.
     *
     * @param logResponses {@code true} to enable response logging
     * @return {@code this}
     */
    public T logResponses(Boolean logResponses) {
        this.logResponses = logResponses;
        return (T) this;
    }

    /**
     * Sets the HTTP request timeout. Defaults to 60 seconds.
     *
     * @param timeout the request timeout
     * @return {@code this}
     */
    public T timeout(Duration timeout) {
        this.timeout = timeout;
        return (T) this;
    }

    /**
     * Sets a custom {@link Authenticator} for generating bearer tokens.
     * Use this instead of {@link #apiKey} when you need a non-standard authentication flow.
     *
     * @param authenticator the authenticator
     * @return {@code this}
     */
    public T authenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
        return (T) this;
    }

    /**
     * Sets a custom {@link HttpClient} to use for all API calls.
     *
     * @param httpClient the HTTP client
     * @return {@code this}
     */
    public T httpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        return (T) this;
    }

    /**
     * Controls whether SSL certificate verification is performed. Defaults to {@code true}.
     * Set to {@code false} only in non-production environments.
     *
     * @param verifySsl {@code false} to disable SSL verification
     * @return {@code this}
     */
    public T verifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
        return (T) this;
    }
}
