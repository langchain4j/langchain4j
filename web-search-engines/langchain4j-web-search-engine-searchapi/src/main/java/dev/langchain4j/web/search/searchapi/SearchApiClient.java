package dev.langchain4j.web.search.searchapi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.time.Duration;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

class SearchApiClient {    
    private final SearchApiAPI searchapiAPI;    
    private final boolean logRequests;
    
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    @Builder
    public SearchApiClient(final String baseUrl, final Duration timeout, final boolean logRequests) {

        this.logRequests = logRequests;

        final OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);
        
        if (this.logRequests) {
            final HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            okHttpClientBuilder.addInterceptor(interceptor);
        }

        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        this.searchapiAPI = retrofit.create(SearchApiAPI.class);
    }

    public SearchApiResponse search(SearchApiSearchRequest searchRequest) {
        try {
        	final Response<SearchApiResponse> retrofitResponse = searchapiAPI
                    .search(searchRequest)
                    .execute();
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
