package dev.langchain4j.model.azure;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.azure.InternalAzureOpenAiHelper.DEFAULT_USER_AGENT;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Configures the OpenAI Responses API client for Azure OpenAI, as the Azure OpenAI SDK does not support this API.
 */
class InternalAzureOpenAiResponsesHelper {

    static final String AZURE_OPENAI_SCOPE = "https://cognitiveservices.azure.com/.default";

    private static final String OPENAI_PATH = "/openai";
    private static final String VERSION_PATH = "/v1";
    private static final String API_PATH = OPENAI_PATH + VERSION_PATH;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(5);

    private InternalAzureOpenAiResponsesHelper() {}

    /**
     * Turns {@code https://{resource}.openai.azure.com/} into {@code https://{resource}.openai.azure.com/openai/v1}.
     * An endpoint already ending with {@code /openai/v1} is kept as is, one ending with {@code /openai} is completed.
     */
    static String responsesBaseUrl(String endpoint) {
        String baseUrl = ensureNotBlank(endpoint, "endpoint").trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith(API_PATH)) {
            return baseUrl;
        }
        return baseUrl.endsWith(OPENAI_PATH) ? baseUrl + VERSION_PATH : baseUrl + API_PATH;
    }

    /**
     * The deployment name is sent as the model name, so it can also come from the default request parameters.
     */
    static void ensureDeploymentName(String deploymentName, ChatRequestParameters defaultRequestParameters) {
        String modelName = defaultRequestParameters == null ? null : defaultRequestParameters.modelName();
        ensureNotBlank(getOrDefault(deploymentName, modelName), "deploymentName");
    }

    /**
     * Creates the supplier of the headers sent with each request: the User-Agent, the Azure authentication header
     * ({@code api-key} or a Microsoft Entra ID bearer token) and the custom headers, which take precedence.
     * Fetching a Microsoft Entra ID token is bounded by the timeout, defaulting to 60 seconds.
     */
    static Supplier<Map<String, String>> headersSupplier(
            String apiKey,
            TokenCredential tokenCredential,
            Duration timeout,
            String userAgentSuffix,
            Supplier<Map<String, String>> customHeadersSupplier) {

        if (isNullOrBlank(apiKey) && tokenCredential == null) {
            throw illegalArgument("Either apiKey or tokenCredential must be provided");
        }
        if (!isNullOrBlank(apiKey) && tokenCredential != null) {
            throw illegalArgument("Only one of apiKey or tokenCredential must be provided");
        }

        String userAgent =
                isNullOrBlank(userAgentSuffix) ? DEFAULT_USER_AGENT : DEFAULT_USER_AGENT + "-" + userAgentSuffix;
        CachingTokenSupplier tokenSupplier = tokenCredential == null
                ? null
                : new CachingTokenSupplier(tokenCredential, getOrDefault(timeout, DEFAULT_TIMEOUT));

        return () -> {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", userAgent);
            if (tokenSupplier == null) {
                headers.put("api-key", apiKey);
            } else {
                headers.put("Authorization", "Bearer " + tokenSupplier.get());
            }
            Map<String, String> customHeaders = customHeadersSupplier == null ? null : customHeadersSupplier.get();
            if (customHeaders != null) {
                headers.putAll(customHeaders);
            }
            return headers;
        };
    }

    /**
     * Applies the timeout to the HTTP client builder. When no timeout is set, the timeouts already configured
     * on the HTTP client builder are kept, defaulting to 60 seconds like the other Azure OpenAI models.
     */
    static HttpClientBuilder httpClientBuilder(HttpClientBuilder httpClientBuilder, Duration timeout) {
        HttpClientBuilder builder = getOrDefault(httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        return builder.connectTimeout(getOrDefault(timeout, getOrDefault(builder.connectTimeout(), DEFAULT_TIMEOUT)))
                .readTimeout(getOrDefault(timeout, getOrDefault(builder.readTimeout(), DEFAULT_TIMEOUT)));
    }

    /**
     * Fetches a Microsoft Entra ID token and reuses it until it is about to expire.
     * <p>
     * A token about to expire is refreshed in the background while it is still used, so callers only wait
     * when there is no valid token, and for at most the timeout. Concurrent callers share the same refresh.
     */
    static class CachingTokenSupplier implements Supplier<String> {

        private final TokenCredential tokenCredential;
        private final Duration timeout;
        private final TokenRequestContext tokenRequestContext = new TokenRequestContext().addScopes(AZURE_OPENAI_SCOPE);
        private AccessToken accessToken;
        private CompletableFuture<AccessToken> refresh;

        CachingTokenSupplier(TokenCredential tokenCredential, Duration timeout) {
            this.tokenCredential = tokenCredential;
            this.timeout = timeout;
        }

        @Override
        public String get() {
            CompletableFuture<AccessToken> pendingRefresh;
            synchronized (this) {
                if (isValidFor(TOKEN_REFRESH_MARGIN)) {
                    return accessToken.getToken();
                }
                pendingRefresh = refresh();
                if (isValidFor(Duration.ZERO)) {
                    return accessToken.getToken();
                }
            }
            return await(pendingRefresh).getToken();
        }

        private boolean isValidFor(Duration duration) {
            return accessToken != null
                    && accessToken.getExpiresAt() != null
                    && OffsetDateTime.now().plus(duration).isBefore(accessToken.getExpiresAt());
        }

        private CompletableFuture<AccessToken> refresh() {
            if (refresh != null) {
                return refresh;
            }
            CompletableFuture<AccessToken> newRefresh =
                    tokenCredential.getToken(tokenRequestContext).toFuture();
            refresh = newRefresh;
            // may complete right away, in which case the token is already updated when returning
            newRefresh.whenComplete((token, error) -> onRefreshCompleted(newRefresh, token));
            return newRefresh;
        }

        private synchronized void onRefreshCompleted(
                CompletableFuture<AccessToken> completedRefresh, AccessToken token) {
            if (token != null) {
                accessToken = token;
            }
            if (refresh == completedRefresh) {
                refresh = null;
            }
        }

        private AccessToken await(CompletableFuture<AccessToken> pendingRefresh) {
            try {
                AccessToken token = pendingRefresh.get(timeout.toMillis(), MILLISECONDS);
                if (token == null) {
                    throw new AuthenticationException("No Microsoft Entra ID token was returned");
                }
                return token;
            } catch (java.util.concurrent.TimeoutException | CancellationException e) {
                // cancelling the refresh lets the next call start a new one instead of waiting for a stuck one
                pendingRefresh.cancel(true);
                throw new TimeoutException("Timed out after " + timeout + " fetching a Microsoft Entra ID token", e);
            } catch (ExecutionException e) {
                throw new AuthenticationException("Failed to fetch a Microsoft Entra ID token", e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LangChain4jException("Interrupted while fetching a Microsoft Entra ID token", e);
            }
        }
    }
}
