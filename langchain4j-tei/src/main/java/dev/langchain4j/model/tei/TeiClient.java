package dev.langchain4j.model.tei;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.model.tei.client.EmbeddingRequest;
import dev.langchain4j.model.tei.client.EmbeddingResponse;
import dev.langchain4j.model.tei.client.ReRankResult;
import dev.langchain4j.model.tei.client.RerankRequest;
import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

public class TeiClient {

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    private final TeiApi teiApi;

    @Builder
    public TeiClient(String baseUrl, Duration timeout) {

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        teiApi = retrofit.create(TeiApi.class);
    }


    public EmbeddingResponse embedding(EmbeddingRequest request) {
        try {
            Response<EmbeddingResponse> retrofitResponse = teiApi.embedding(request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<ReRankResult> rerank(RerankRequest request) {
        try {
            Response<List<ReRankResult>> retrofitResponse = teiApi.rerank(request).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static RuntimeException toException(Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }

}
