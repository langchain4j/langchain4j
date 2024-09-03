package dev.langchain4j.model.ovhai.internal.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingRequest;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingResponse;
import dev.langchain4j.model.ovhai.internal.api.OvhAiApi;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.List;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class DefaultOvhAiClient extends OvhAiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private final OkHttpClient okHttpClient;

    private final String apiKey;
    private final boolean logResponses;
    private final OvhAiApi ovhAiApi;
    private final String authorizationHeader;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends OvhAiClient.Builder<DefaultOvhAiClient, Builder> {

        public DefaultOvhAiClient build() {
            return new DefaultOvhAiClient(this);
        }
    }

    DefaultOvhAiClient(Builder builder) {
        if (isNullOrBlank(builder.apiKey)) {
            throw new IllegalArgumentException(
                "OVHcloud API key must be defined. It can be generated here: https://endpoints.ai.cloud.ovh.net/"
            );
        }

        this.apiKey = builder.apiKey;
        this.logResponses = builder.logResponses;

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
            .callTimeout(builder.timeout)
            .connectTimeout(builder.timeout)
            .readTimeout(builder.timeout)
            .writeTimeout(builder.timeout);

        if (builder.logRequests) {
            okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
        }
        if (logResponses) {
            okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());
        }

        this.okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(Utils.ensureTrailingForwardSlash(ensureNotBlank(builder.baseUrl, "baseUrl")))
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
            .build();

        this.ovhAiApi = retrofit.create(OvhAiApi.class);
        this.authorizationHeader = "Bearer " + ensureNotBlank(apiKey, "apiKey");
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        try {
            retrofit2.Response<List<float[]>> retrofitResponse = ovhAiApi.embed(request, authorizationHeader).execute();

            if (retrofitResponse.isSuccessful()) {
                return new EmbeddingResponse(retrofitResponse.body());
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
