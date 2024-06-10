package dev.langchain4j.model.workersai.client;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

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
     * @param authToken
     *      authorization token
     * @return
     *      api
     */
    public static WorkersAiApi createService(String authToken) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(authToken))
                // Slow but can be needed for images
                .callTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(WorkersAiApi.class);
    }

    /**
     * An interceptor for HTTP requests to add an authorization token to the header.
     * Implements the {@link Interceptor} interface.
     */
    public static class AuthInterceptor implements Interceptor {
        private final String authToken;

        /**
         * Constructs an AuthInterceptor with a specified authorization token.
         *
         * @param authToken The authorization token to be used in HTTP headers.
         */
        public AuthInterceptor(String authToken) {
            this.authToken = authToken;
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
                    .header("Authorization", "Bearer " + authToken);
            Request request = builder.build();
            return chain.proceed(request);
        }
    }


}
