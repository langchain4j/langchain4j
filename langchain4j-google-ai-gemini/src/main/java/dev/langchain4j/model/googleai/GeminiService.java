package dev.langchain4j.model.googleai;

//import io.reactivex.rxjava3.core.Observable;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Header;
import retrofit2.http.Headers;

import java.time.Duration;
//import retrofit2.http.Streaming;

interface GeminiService {
    String GEMINI_AI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/";
    String API_KEY_HEADER_NAME = "x-goog-api-key";
    String USER_AGENT = "User-Agent: LangChain4j";

    static GeminiService getGeminiService(Logger logger, Duration timeout) {
        Retrofit.Builder retrofitBuilder = new Retrofit.Builder()
            .baseUrl(GEMINI_AI_ENDPOINT)
            .addConverterFactory(GsonConverterFactory.create());

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
            .callTimeout(timeout);

        if (logger != null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(logger::debug);
            logging.redactHeader(API_KEY_HEADER_NAME);
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            clientBuilder.addInterceptor(logging);
        }

        retrofitBuilder.client(clientBuilder.build());
        Retrofit retrofit = retrofitBuilder.build();

        return retrofit.create(GeminiService.class);
    }

    @POST("models/{model}:generateContent")
    @Headers(USER_AGENT)
    Call<GeminiGenerateContentResponse> generateContent(
        @Path("model") String modelName,
        @Header(API_KEY_HEADER_NAME) String apiKey,
        @Body GeminiGenerateContentRequest request);

    @POST("models/{model}:countTokens")
    @Headers(USER_AGENT)
    Call<GeminiCountTokensResponse> countTokens(
        @Path("model") String modelName,
        @Header(API_KEY_HEADER_NAME) String apiKey,
        @Body GeminiCountTokensRequest countTokensRequest);

    @POST("models/{model}:embedContent")
    @Headers(USER_AGENT)
    Call<GoogleAiEmbeddingResponse> embed(
        @Path("model") String modelName,
        @Header(API_KEY_HEADER_NAME) String apiKey,
        @Body GoogleAiEmbeddingRequest embeddingRequest);

    @POST("models/{model}:batchEmbedContents")
    @Headers(USER_AGENT)
    Call<GoogleAiBatchEmbeddingResponse> batchEmbed(
        @Path("model") String modelName,
        @Header(API_KEY_HEADER_NAME) String apiKey,
        @Body GoogleAiBatchEmbeddingRequest batchEmbeddingRequest);

/*
    @Streaming
    @POST("models/{model}:streamGenerateContent")
    @Headers("User-Agent: LangChain4j")
    Observable<GeminiGenerateContentResponse> streamGenerateContent(
        @Path("model") String modelName,
        @Header(API_KEY_HEADER_NAME) String apiKey,
        @Body GeminiGenerateContentRequest request);
*/


}
