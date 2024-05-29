package dev.langchain4j.web.search.searchapi;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

import java.io.IOException;
import java.time.Duration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

class SearchApiClient {    
    private final SearchApiAPI searchapiAPI;
    private final String engine;
    private final String apiKey;
    
    /* Indicates whether to log requests and their responses. Defaults to false. */
    private final boolean logRequests;
    
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .create();

    @Builder
    public SearchApiClient(final String baseUrl,
    					final String apiKey,
    					final String engine,
    					final Duration timeout, 
    					final boolean logRequests) {
        this.apiKey = apiKey;
        this.engine = engine;
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

    public SearchApiResponse search(final SearchApiSearchRequest searchRequest) {
        try {
        	final Response<JsonObject> retrofitResponse = searchapiAPI
                    .search(apiKey, 
                    		engine, 
                    		searchRequest.getQ(),
                    		searchRequest.getOptionalParameters())
                    .execute();
            if (retrofitResponse.isSuccessful()) {
            	final SearchApiResponse resp = SearchApiResponse.builder().build();
            	resp.setResults(retrofitResponse.body());
//                System.out.println("########################################################################");
//                System.out.println("########################################################################");
//                System.out.println("" + new Gson().toJson(resp.getResults()));
                return resp;
            } else {
                throw toException(retrofitResponse);
            }
            
        } catch (IOException e) {
            throw new RuntimeException(e);  
        } 
    }

    private static RuntimeException toException(final Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
    
}
