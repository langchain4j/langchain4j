package dev.langchain4j.model.qianfan.client;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class AuthorizationHeaderInjector implements Interceptor {
    private final String apiKey;

    public AuthorizationHeaderInjector(String apiKey) {
        this.apiKey = apiKey;
    }

    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request().newBuilder().addHeader("Authorization", "Bearer " + this.apiKey).build();
        return chain.proceed(request);
    }
}
