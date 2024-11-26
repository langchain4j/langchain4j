package dev.langchain4j.model.watsonx.internal.api;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class WatsonxTokenInjectorInterceptor implements Interceptor {

    private static final String BEARER_TOKEN = "Bearer ";

    private final String apiKey;

    public WatsonxTokenInjectorInterceptor(String apiKey) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request()
            .newBuilder()
            .addHeader("Authorization", BEARER_TOKEN + apiKey)
            .build();

        return chain.proceed(request);

    }
}
