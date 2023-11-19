package dev.langchain4j.image.openai.dalle;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofSeconds;

import java.io.IOException;
import java.net.Proxy;
import java.time.Duration;
import lombok.Builder;
import lombok.NonNull;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

final class OpenAiDalleClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiDalleClient.class);
  private static final String OPENAI_URL = "https://api.openai.com/v1/";

  private final OkHttpClient client;
  private final OpenAiDalleApi api;

  @Builder
  public OpenAiDalleClient(
    @NonNull String openAiApiKey,
    Duration callTimeout,
    Duration connectTimeout,
    Duration readTimeout,
    Duration writeTimeout,
    Proxy proxy,
    boolean logRequests,
    boolean logResponses
  ) {
    OkHttpClient.Builder cBuilder =
      (new OkHttpClient.Builder()).callTimeout(getOrDefault(callTimeout, ofSeconds(60)))
        .connectTimeout(getOrDefault(connectTimeout, ofSeconds(60)))
        .readTimeout(getOrDefault(readTimeout, ofSeconds(60)))
        .writeTimeout(getOrDefault(writeTimeout, ofSeconds(60)));
    cBuilder.addInterceptor(new AuthorizationHeaderInjector(openAiApiKey));

    if (proxy != null) {
      cBuilder.proxy(proxy);
    }

    if (logRequests) {
      cBuilder.addInterceptor(new RequestLoggingInterceptor());
    }

    if (logResponses) {
      cBuilder.addInterceptor(new ResponseLoggingInterceptor());
    }

    client = cBuilder.build();
    Retrofit retrofit =
      (new Retrofit.Builder()).baseUrl(OPENAI_URL)
        .client(this.client)
        .addConverterFactory(GsonConverterFactory.create())
        .build();
    api = retrofit.create(OpenAiDalleApi.class);
  }

  OpenAiDalleResponse generate(OpenAiDalleRequest request) {
    try {
      retrofit2.Response<OpenAiDalleResponse> response = api.generateImage(request).execute();

      if (response.isSuccessful()) {
        return response.body();
      } else {
        throw toException(response);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static RuntimeException toException(retrofit2.Response<?> response) throws IOException {
    return new RuntimeException(
      String.format("status code: %s; body: %s", response.code(), response.errorBody().string())
    );
  }
}
