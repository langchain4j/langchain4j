package dev.langchain4j.store.embedding.chroma;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

class ChromaClient {

    private final ChromaApi chromaApi;

    ChromaClient(String baseUrl, Duration timeout) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .callTimeout(timeout)
            .connectTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout)
            .addInterceptor(new ChromaRequestLoggingInterceptor()) // TODO add "if"
            .addInterceptor(new ChromaResponseLoggingInterceptor()) // TODO add "if"
            .build();

        Gson gson = new GsonBuilder().setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES).create();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build();

        this.chromaApi = retrofit.create(ChromaApi.class);
    }

    Collection createCollection(CreateCollectionRequest createCollectionRequest) {
        try {
            Response<Collection> response = chromaApi.createCollection(createCollectionRequest).execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Collection collection(String collectionName) {
        try {
            Response<Collection> response = chromaApi.collection(collectionName).execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                // if collection is not present, Chroma returns: Status - 500
                return null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    boolean addEmbeddings(String collectionId, AddEmbeddingsRequest addEmbeddingsRequest) {
        try {
            Response<Boolean> retrofitResponse = chromaApi.addEmbeddings(collectionId, addEmbeddingsRequest).execute();
            if (retrofitResponse.isSuccessful()) {
                return Boolean.TRUE.equals(retrofitResponse.body());
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    QueryResponse queryCollection(String collectionId, QueryRequest queryRequest) {
        try {
            Response<QueryResponse> retrofitResponse = chromaApi.queryCollection(collectionId, queryRequest).execute();
            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void deleteEmbeddings(String collectionId, DeleteEmbeddingsRequest deleteEmbeddingsRequest) {
        try {
            Response<List<String>> retrofitResponse = chromaApi
                .deleteEmbeddings(collectionId, deleteEmbeddingsRequest)
                .execute();
            if (retrofitResponse.isSuccessful()) {
                // TODO do it right
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
