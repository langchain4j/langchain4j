package dev.langchain4j.model.huggingface;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

class HuggingFaceClient {

    private final HuggingFaceApi huggingFaceApi;

    HuggingFaceClient(String apiKey, String modelId, Duration timeout) {

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new ApiKeyInsertingInterceptor(apiKey))
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();

        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api-inference.huggingface.co/pipeline/feature-extraction/" + modelId + "/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        this.huggingFaceApi = retrofit.create(HuggingFaceApi.class);
    }

    List<float[]> embed(EmbeddingRequest request) {
        try {
            Response<List<float[]>> retrofitResponse = huggingFaceApi.embed(request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
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
