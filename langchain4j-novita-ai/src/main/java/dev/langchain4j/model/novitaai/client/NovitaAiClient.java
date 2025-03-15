package dev.langchain4j.model.novitaai.client;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.time.Duration;

/**
 * Low level client to interact with the WorkerAI API.
 */
public class NovitaAiClient {

    private static final String BASE_URL = "https://api.novita.ai/v3/openai/";

    /**
     * Constructor.
     */
    public NovitaAiClient() {}

    /**
     * Initialization of okHTTP.
     *
     * @param apiKey
     *      authorization token
     * @return
     *      api
     */
    public static NovitaAiApi createService(String apiKey) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(apiKey))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        return retrofit.create(NovitaAiApi.class);
    }

    /**
     * An interceptor for HTTP requests to add an authorization token to the header.
     * Implements the {@link Interceptor} interface.
     */
    public static class AuthInterceptor implements Interceptor {
        private final String apiKey;

        /**
         * Constructs an AuthInterceptor with a specified authorization token.
         *
         * @param apiKey The authorization token to be used in HTTP headers.
         */
        public AuthInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * Intercepts an outgoing HTTP request, adding an authorization header.
         *
         * @param chain The chain of request/response interceptors.
         * @return The modified response after adding the authorization header.
         * @throws IOException If an IO exception occurs during request processing.
         */
        @NotNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request.Builder builder = chain
                    .request().newBuilder()
                    .header("Authorization", "Bearer " + apiKey);
            Request request = builder.build();
            return chain.proceed(request);
        }
    }


}
