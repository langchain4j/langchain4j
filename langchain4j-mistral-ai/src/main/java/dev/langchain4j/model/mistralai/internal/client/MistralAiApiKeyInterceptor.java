package dev.langchain4j.model.mistralai.internal.client;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

class MistralAiApiKeyInterceptor implements Interceptor {

    private final String apiKey;

    MistralAiApiKeyInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request()
                .newBuilder()
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();
        return chain.proceed(request);
    }
}
