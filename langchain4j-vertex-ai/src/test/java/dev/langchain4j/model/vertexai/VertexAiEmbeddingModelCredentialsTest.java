package dev.langchain4j.model.vertexai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link VertexAiEmbeddingModel} credentials propagation to
 * {@link com.google.cloud.aiplatform.v1beta1.LlmUtilityServiceSettings}.
 *
 * <p>This verifies the fix for langchain4j/langchain4j#4837: when explicit credentials
 * are provided via {@code .credentials()}, they must be propagated to both
 * PredictionServiceSettings (used for predict calls) AND LlmUtilityServiceSettings
 * (used for token counting / computeTokens).
 */
class VertexAiEmbeddingModelCredentialsTest {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    /**
     * Verifies that when explicit credentials are provided, the {@code calculateTokensCounts}
     * method does NOT throw {@code java.io.IOException} about missing default credentials.
     *
     * <p>Before the fix (bug #4837): LlmUtilityServiceSettings did not receive the explicit
     * credentials, so it fell back to Application Default Credentials (ADC). When ADC was
     * unavailable, {@code calculateTokensCounts} threw:
     * <pre>java.io.IOException: Your default credentials were not found...</pre>
     *
     * <p>After the fix: LlmUtilityServiceSettings receives the same explicit credentials
     * as PredictionServiceSettings, so no ADC lookup occurs. Instead, if the endpoint is
     * unreachable, a gRPC connection error is thrown (which is expected in a unit test
     * environment with a fake endpoint).
     */
    @Test
    void should_propagate_credentials_to_llm_utility_settings() {
        // given
        GoogleCredentials fakeCredentials = mock(GoogleCredentials.class);
        when(fakeCredentials.createScoped(any(String.class))).thenReturn(fakeCredentials);

        // Build model with explicit credentials (no ADC in test env)
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
                .endpoint("https://fake-aiplatform.googleapis.com:443")
                .project("test-project")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-005")
                .credentials(fakeCredentials)
                .build();

        // when / then
        // The previous bug threw java.io.IOException: "Your default credentials were not found."
        // because LlmUtilityServiceSettings fell back to ADC.
        // With the fix, it should NOT throw that specific IOException.
        // It may throw a gRPC connection error (expected in this test environment),
        // but NOT an IOException about missing credentials.
        try {
            model.calculateTokensCounts(List.of(TextSegment.from("hello world")));
            // If no exception, client creation succeeded — credentials were propagated.
        } catch (RuntimeException e) {
            // Any RuntimeException is fine (e.g. gRPC connection refused to fake host),
            // BUT the root cause must NOT be "default credentials were not found".
            String rootMessage = getRootCauseMessage(e);
            assertThat(rootMessage)
                    .describedAs("Should NOT fail due to missing ADC")
                    .doesNotContain("default credentials were not found");
            assertThat(rootMessage)
                    .describedAs("Should NOT fail due to ADC fallback")
                    .doesNotContain("Application Default Credentials");
        }
    }

    /**
     * Verifies that when credentials are set on the builder, {@code createScoped} is called
     * with the correct Google Cloud Platform auth scope.
     */
    @Test
    void should_scope_credentials_with_cloud_platform_scope() {
        // given
        GoogleCredentials fakeCredentials = mock(GoogleCredentials.class);
        when(fakeCredentials.createScoped(any(String.class))).thenReturn(fakeCredentials);

        // Build model
        VertexAiEmbeddingModel.builder()
                .endpoint("https://fake-aiplatform.googleapis.com:443")
                .project("test-project")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-005")
                .credentials(fakeCredentials)
                .build();

        // then
        verify(fakeCredentials).createScoped(CLOUD_PLATFORM_SCOPE);
    }

    /**
     * Verifies that when NO credentials are set on the builder (the default case), no
     * scoped credentials are created and the model still builds successfully.
     */
    @Test
    void should_build_without_explicit_credentials() {
        // given - no credentials set

        // when
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
                .endpoint("https://fake-aiplatform.googleapis.com:443")
                .project("test-project")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-005")
                .build();

        // then - model builds without error (ADC will be used at runtime)
        assertThat(model).isNotNull();
    }

    private static String getRootCauseMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
