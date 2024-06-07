package dev.langchain4j.model.ovhai.internal.client;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import java.io.IOException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingRequest;
import dev.langchain4j.model.ovhai.internal.api.EmbeddingResponse;
import dev.langchain4j.model.ovhai.internal.api.OvhAiApi;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class DefaultOvhAiClient extends OvhAiClient {
  private static final Gson GSON = new GsonBuilder()
      .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOvhAiClient.class);

  private final OkHttpClient okHttpClient;

  private final String apiKey;
  private final boolean logResponses;
  private final OvhAiApi ovhAiApi;
  private final String authorizationHeader;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends OvhAiClient.Builder<DefaultOvhAiClient, Builder> {

    public DefaultOvhAiClient build() {
      return new DefaultOvhAiClient(this);
    }
  }

  DefaultOvhAiClient(Builder builder) {
    if (isNullOrBlank(builder.apiKey)) {
      throw new IllegalArgumentException("Anthropic API key must be defined. "
          + "It can be generated here: https://console.anthropic.com/settings/keys");
    }

    this.apiKey = builder.apiKey;
    this.logResponses = builder.logResponses;

    OkHttpClient.Builder okHttpClientBuilder =
        new OkHttpClient.Builder().callTimeout(builder.timeout).connectTimeout(builder.timeout)
            .readTimeout(builder.timeout).writeTimeout(builder.timeout);

    if (builder.logRequests) {
      okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
    }
    if (logResponses) {
      okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());
    }

    this.okHttpClient = okHttpClientBuilder.build();


    Retrofit retrofit = new Retrofit.Builder().baseUrl(ensureNotBlank(builder.baseUrl, "baseUrl"))
        .client(okHttpClient).addConverterFactory(GsonConverterFactory.create(GSON)).build();

    this.ovhAiApi = retrofit.create(OvhAiApi.class);
    this.authorizationHeader = "Bearer " + ensureNotBlank(apiKey, "apiKey");

  }

  public EmbeddingResponse embed(EmbeddingRequest request) {
    try {
      retrofit2.Response<float[]> retrofitResponse =
          ovhAiApi.embed(request, authorizationHeader).execute();

      if (retrofitResponse.isSuccessful()) {
        return new EmbeddingResponse(Arrays.asList(retrofitResponse.body()));
      } else {
        throw toException(retrofitResponse);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static RuntimeException toException(retrofit2.Response<?> response) throws IOException {
    int code = response.code();
    String body = response.errorBody().string();
    String errorMessage = String.format("status code: %s; body: %s", code, body);
    return new RuntimeException(errorMessage);
  }
}
