package dev.langchain4j.web.search.searchapi;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

import java.io.IOException;
//import java.lang.reflect.Type;
import java.time.Duration;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
//import com.google.gson.JsonDeserializationContext;
//import com.google.gson.JsonDeserializer;
//import com.google.gson.JsonElement;
//import com.google.gson.JsonObject;
//import com.google.gson.JsonParseException;
//import com.google.gson.JsonPrimitive;
//import com.google.gson.JsonSerializationContext;
//import com.google.gson.JsonSerializer;

import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SearchApiClient {    
    private final SearchApiAPI searchapiAPI;
    private final String engine;
    private final String apiKey;
        
    /* Indicates whether to log requests and their responses. Defaults to false. */
    private final boolean logRequests;
    
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            //.registerTypeAdapter(LocalDateTime.class, new SearchApiClient.LocalDateTimeAdapter())
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
        	final Response<SearchApiResponse> retrofitResponse = searchapiAPI
                    .search(apiKey, 
                    		engine, 
                    		searchRequest.getQ(),
                    		searchRequest.getOptionalParameters())
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

    private static RuntimeException toException(final Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();
        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
/*    
    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);
    	  
    	@Override
    	public JsonElement serialize(final LocalDateTime time, final Type typeOfSrc, final JsonSerializationContext context) {
    	    return new JsonPrimitive(time.format(formatter));
    	}
    	
    	@Override
    	public LocalDateTime deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
    	throws JsonParseException {
    	    return LocalDateTime.parse(json.getAsString(),
    	        DateTimeFormatter.ofPattern(DATE_FORMAT).withLocale(Locale.ENGLISH));
    	}
    }
*/    
}
