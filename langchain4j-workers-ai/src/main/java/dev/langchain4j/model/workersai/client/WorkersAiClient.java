package dev.langchain4j.model.workersai.client;

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
public class WorkersAiClient {

    private static final String BASE_URL = "https://api.cloudflare.com/";

    /**
     * Constructor.
     */
    public WorkersAiClient() {}

    /**
     * Initialization of okHTTP.
     *
     * @param apiToken
     *      authorization token
     * @return
     *      api
     */
    public static WorkersAiApi createService(String apiToken) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(apiToken))
                // Slow but can be needed for images
                .callTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        return retrofit.create(WorkersAiApi.class);
    }

    /**
     * An interceptor for HTTP requests to add an authorization token to the header.
     * Implements the {@link Interceptor} interface.
     */
    public static class AuthInterceptor implements Interceptor {
        private final String apiToken;

        /**
         * Constructs an AuthInterceptor with a specified authorization token.
         *
         * @param apiToken The authorization token to be used in HTTP headers.
         */
        public AuthInterceptor(String apiToken) {
            this.apiToken = apiToken;
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
                    .header("Authorization", "Bearer " + apiToken);
            Request request = builder.build();
            return chain.proceed(request);
        }
    }


}
