package dev.langchain4j.model.watsonx.internal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.internal.Utils;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;


public class DefaultWatsonxAiClient extends WatsonxAiClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private final WatsonxApi watsonxApi;

    DefaultWatsonxAiClient(Builder builder) {
        OkHttpClient client = configureHttpClient(builder);
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(Utils.ensureTrailingForwardSlash(builder.baseUrl))
            .client(client)
            .addConverterFactory(JacksonConverterFactory.create(OBJECT_MAPPER))
            .build();
        watsonxApi = retrofit.create(WatsonxApi.class);
    }


    private OkHttpClient configureHttpClient(Builder builder) {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
            .callTimeout(builder.timeout)
            .connectTimeout(builder.timeout)
            .readTimeout(builder.timeout)
            .writeTimeout(builder.timeout)
            .addInterceptor(new WatsonxTokenInjectorInterceptor(builder.token));

        if (builder.logRequests) {
            clientBuilder.addInterceptor(new WatsonxAiLoggingInterceptor());
        }
        if (builder.logResponses) {
            clientBuilder.addInterceptor(new WatsonxAiResponseLoggingInterceptor());
        }

        return clientBuilder.build();
    }


    @Override
    public WatsonxAiChatCompletionResponse chatCompletion(WatsonxChatCompletionRequest request, String version) {
        try {
            Response<WatsonxAiChatCompletionResponse> response = watsonxApi.chat(request, version).execute();
            if (response.isSuccessful()) {
                return response.body();
            }
            throw new RuntimeException(response.errorBody().string());
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends WatsonxAiClient.Builder<DefaultWatsonxAiClient, Builder> {

        public DefaultWatsonxAiClient build() {
            return new DefaultWatsonxAiClient(this);
        }
    }
}
