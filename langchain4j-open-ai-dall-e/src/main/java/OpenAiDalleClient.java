import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.image.ImageRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

final class OpenAiDalleClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiDalleClient.class);
  private final String baseUrl;
  private final OkHttpClient client;
  private final OpenAiDalleApi api;

  private OpenAiDalleClient(Builder serviceBuilder) {
    this.baseUrl = serviceBuilder.baseUrl;
    OkHttpClient.Builder cBuilder =
      (new OkHttpClient.Builder()).callTimeout(serviceBuilder.callTimeout)
        .connectTimeout(serviceBuilder.connectTimeout)
        .readTimeout(serviceBuilder.readTimeout)
        .writeTimeout(serviceBuilder.writeTimeout);
    if (serviceBuilder.openAiApiKey == null && serviceBuilder.azureApiKey == null) {
      throw new IllegalArgumentException("openAiApiKey OR azureApiKey must be defined");
    } else if (serviceBuilder.openAiApiKey != null && serviceBuilder.azureApiKey != null) {
      throw new IllegalArgumentException("openAiApiKey AND azureApiKey cannot both be defined at the same time");
    } else {
      if (serviceBuilder.openAiApiKey != null) {
        cBuilder.addInterceptor(new AuthorizationHeaderInjector(serviceBuilder.openAiApiKey));
      }/*else {
                cBuilder.addInterceptor(new ApiKeyHeaderInjector(serviceBuilder.azureApiKey));
            }*/

      if (serviceBuilder.proxy != null) {
        cBuilder.proxy(serviceBuilder.proxy);
      }

      if (serviceBuilder.logRequests) {
        cBuilder.addInterceptor(new RequestLoggingInterceptor());
      }

      if (serviceBuilder.logResponses) {
        cBuilder.addInterceptor(new ResponseLoggingInterceptor());
      }

      this.client = cBuilder.build();
      Retrofit retrofit =
        (new Retrofit.Builder()).baseUrl(serviceBuilder.baseUrl)
          .client(this.client)
          .addConverterFactory(GsonConverterFactory.create())
          .build();
      this.api = retrofit.create(OpenAiDalleApi.class);
    }
  }

  public void shutdown() {
    this.client.dispatcher().executorService().shutdown();
    this.client.connectionPool().evictAll();
    Cache cache = this.client.cache();
    if (cache != null) {
      try {
        cache.close();
      } catch (IOException var3) {
        log.error("Failed to close cache", var3);
      }
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  Image generate(ImageRequest request) {
    try {
      retrofit2.Response<Image> response = api.generateImage(request).execute();

      //            if (response.isSuccessful()) {
      return response.body();
      /*} else {
                throw toException(response);
            }*/
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class Builder {

    private String baseUrl;
    private String openAiApiKey;
    private String azureApiKey;
    private Duration callTimeout;
    private Duration connectTimeout;
    private Duration readTimeout;
    private Duration writeTimeout;
    private Proxy proxy;
    private boolean logRequests;
    private boolean logResponses;
    private boolean logStreamingResponses;
    private String model;

    private Builder() {
      this.baseUrl = "https://api.openai.com/v1/";
      this.callTimeout = Duration.ofSeconds(60L);
      this.connectTimeout = Duration.ofSeconds(60L);
      this.readTimeout = Duration.ofSeconds(60L);
      this.writeTimeout = Duration.ofSeconds(60L);
    }

    public Builder baseUrl(String baseUrl) {
      if (baseUrl != null && !baseUrl.trim().isEmpty()) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return this;
      } else {
        throw new IllegalArgumentException("baseUrl cannot be null or empty");
      }
    }

    public Builder openAiApiKey(String openAiApiKey) {
      if (openAiApiKey != null && !openAiApiKey.trim().isEmpty()) {
        this.openAiApiKey = openAiApiKey;
        return this;
      } else {
        throw new IllegalArgumentException(
          "openAiApiKey cannot be null or empty. API keys can be generated here: https://platform.openai.com/account/api-keys"
        );
      }
    }

    public Builder azureApiKey(String azureApiKey) {
      if (azureApiKey != null && !azureApiKey.trim().isEmpty()) {
        this.azureApiKey = azureApiKey;
        return this;
      } else {
        throw new IllegalArgumentException("azureApiKey cannot be null or empty");
      }
    }

    public Builder callTimeout(Duration callTimeout) {
      if (callTimeout == null) {
        throw new IllegalArgumentException("callTimeout cannot be null");
      } else {
        this.callTimeout = callTimeout;
        return this;
      }
    }

    public Builder connectTimeout(Duration connectTimeout) {
      if (connectTimeout == null) {
        throw new IllegalArgumentException("connectTimeout cannot be null");
      } else {
        this.connectTimeout = connectTimeout;
        return this;
      }
    }

    public Builder readTimeout(Duration readTimeout) {
      if (readTimeout == null) {
        throw new IllegalArgumentException("readTimeout cannot be null");
      } else {
        this.readTimeout = readTimeout;
        return this;
      }
    }

    public Builder writeTimeout(Duration writeTimeout) {
      if (writeTimeout == null) {
        throw new IllegalArgumentException("writeTimeout cannot be null");
      } else {
        this.writeTimeout = writeTimeout;
        return this;
      }
    }

    public Builder proxy(Proxy.Type type, String ip, int port) {
      this.proxy = new Proxy(type, new InetSocketAddress(ip, port));
      return this;
    }

    public Builder proxy(Proxy proxy) {
      this.proxy = proxy;
      return this;
    }

    public Builder logRequests() {
      return this.logRequests(true);
    }

    public Builder logRequests(Boolean logRequests) {
      if (logRequests == null) {
        logRequests = false;
      }

      this.logRequests = logRequests;
      return this;
    }

    public Builder logResponses() {
      return this.logResponses(true);
    }

    public Builder logResponses(Boolean logResponses) {
      if (logResponses == null) {
        logResponses = false;
      }

      this.logResponses = logResponses;
      return this;
    }

    public Builder model(String model) {
      this.model = model;
      return this;
    }

    public OpenAiDalleClient build() {
      return new OpenAiDalleClient(this);
    }
  }
}
