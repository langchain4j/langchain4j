package dev.langchain4j.model.huggingface;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

class ApiKeyInsertingInterceptor implements Interceptor {

    private final String apiKey;

    ApiKeyInsertingInterceptor(String apiKey) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
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
