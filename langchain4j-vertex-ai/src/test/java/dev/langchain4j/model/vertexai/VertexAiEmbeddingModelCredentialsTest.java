package dev.langchain4j.model.vertexai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.aiplatform.v1beta1.LlmUtilityServiceSettings;
import com.google.cloud.aiplatform.v1beta1.PredictionServiceSettings;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Verifies that explicit {@link GoogleCredentials} provided via
 * {@link VertexAiEmbeddingModel.Builder#credentials(GoogleCredentials)} are propagated to both
 * {@link PredictionServiceSettings} (used by the {@code predict} API) and
 * {@link LlmUtilityServiceSettings} (used by {@code calculateTokensCounts} for batching).
 *
 * <p>Regression test for https://github.com/langchain4j/langchain4j/issues/4837: before the fix, explicit
 * credentials were only applied to {@code PredictionServiceSettings}, while
 * {@code LlmUtilityServiceSettings} silently fell back to Application Default Credentials (ADC). In
 * environments without ADC, {@code embedAll()} then failed with
 * {@code java.io.IOException: Your default credentials were not found} even though valid explicit
 * credentials were provided.
 */
class VertexAiEmbeddingModelCredentialsTest {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private static final String CLIENT_EMAIL = "fake@fake-project.iam.gserviceaccount.com";

    private static final String ENDPOINT = "us-central1-aiplatform.googleapis.com:443";

    @Test
    void should_propagate_explicit_credentials_to_prediction_and_llm_utility_settings() throws Exception {
        // given
        GoogleCredentials credentials = fakeServiceAccountCredentials();

        // when
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
                .endpoint(ENDPOINT)
                .project("fake-project")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-005")
                .credentials(credentials)
                .build();

        // then
        PredictionServiceSettings predictionSettings = readField(model, "settings", PredictionServiceSettings.class);
        assertThat(predictionSettings.getEndpoint()).isEqualTo(ENDPOINT);
        assertCredentialsArePropagated(predictionSettings.getCredentialsProvider());

        LlmUtilityServiceSettings llmUtilitySettings =
                readField(model, "llmUtilitySettings", LlmUtilityServiceSettings.class);
        assertThat(llmUtilitySettings.getEndpoint()).isEqualTo(ENDPOINT);
        assertCredentialsArePropagated(llmUtilitySettings.getCredentialsProvider());
    }

    @Test
    void should_fall_back_to_default_credentials_when_no_explicit_credentials_are_provided() throws Exception {
        // when
        VertexAiEmbeddingModel model = VertexAiEmbeddingModel.builder()
                .endpoint(ENDPOINT)
                .project("fake-project")
                .location("us-central1")
                .publisher("google")
                .modelName("text-embedding-005")
                .build();

        // then: the (ADC-based) default credentials providers are used
        PredictionServiceSettings predictionSettings = readField(model, "settings", PredictionServiceSettings.class);
        assertThat(predictionSettings.getCredentialsProvider()).isNotInstanceOf(FixedCredentialsProvider.class);

        LlmUtilityServiceSettings llmUtilitySettings =
                readField(model, "llmUtilitySettings", LlmUtilityServiceSettings.class);
        assertThat(llmUtilitySettings.getCredentialsProvider()).isNotInstanceOf(FixedCredentialsProvider.class);
    }

    private static void assertCredentialsArePropagated(CredentialsProvider credentialsProvider) throws IOException {
        assertThat(credentialsProvider).isInstanceOf(FixedCredentialsProvider.class);

        GoogleCredentials propagatedCredentials = (GoogleCredentials) credentialsProvider.getCredentials();
        assertThat(propagatedCredentials).isInstanceOf(ServiceAccountCredentials.class);

        ServiceAccountCredentials serviceAccountCredentials = (ServiceAccountCredentials) propagatedCredentials;
        assertThat(serviceAccountCredentials.getClientEmail()).isEqualTo(CLIENT_EMAIL);
        assertThat(serviceAccountCredentials.getScopes()).containsExactly(CLOUD_PLATFORM_SCOPE);
    }

    private static <T> T readField(VertexAiEmbeddingModel model, String fieldName, Class<T> fieldType)
            throws Exception {
        // VertexAiEmbeddingModel intentionally does not expose the low-level client settings,
        // so the test reads the private fields via reflection (as other tests do, e.g.
        // GoogleAiGeminiImageModelTest#setGeminiService)
        Field field = VertexAiEmbeddingModel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return fieldType.cast(field.get(model));
    }

    private static GoogleCredentials fakeServiceAccountCredentials() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return ServiceAccountCredentials.fromPkcs8(
                "fake-client-id", CLIENT_EMAIL, toPkcs8Pem(keyPair.getPrivate()), "fake-private-key-id", null);
    }

    private static String toPkcs8Pem(PrivateKey privateKey) {
        String base64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        String beginMarker = "-----BEGIN" + " PRIVATE KEY" + "-----";
        String endMarker = "-----END" + " PRIVATE KEY" + "-----";
        StringBuilder pem = new StringBuilder(beginMarker).append('\n');
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        pem.append(endMarker).append('\n');
        return pem.toString();
    }
}
