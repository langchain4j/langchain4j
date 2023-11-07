import dev.ai4j.openai4j.OpenAiClient;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.image.ImageRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.output.Response;
import java.io.IOException;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import lombok.Builder;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofSeconds;

public class OpenAiDalleChatLanguageModel /*implements ChatLanguageModel*/ { // TODO

  private final OpenAiDalleClient client;

  @Builder
  public OpenAiDalleChatLanguageModel(String baseUrl,
                         String apiKey,
                         String modelName,
                         Double temperature,
                         Double topP,
                         List<String> stop,
                         Integer maxTokens,
                         Double presencePenalty,
                         Double frequencyPenalty,
                         Duration timeout,
                         Integer maxRetries,
                         Proxy proxy,
                         Boolean logRequests,
                         Boolean logResponses,
                         Tokenizer tokenizer) {

    baseUrl = getOrDefault(baseUrl, OPENAI_URL);
    if (OPENAI_DEMO_API_KEY.equals(apiKey)) {
      baseUrl = OPENAI_DEMO_URL;
    }

    timeout = getOrDefault(timeout, ofSeconds(60));

    this.client = OpenAiDalleClient.builder()
            .openAiApiKey(apiKey)
            .baseUrl(baseUrl)
            .callTimeout(timeout)
            .connectTimeout(timeout)
            .readTimeout(timeout)
            .writeTimeout(timeout)
            .proxy(proxy)
            .logRequests(logRequests)
            .logResponses(logResponses)
            .build();
  }


  //  @Override
  public Response<Image> generate(ChatMessage message) {
//    OkHttpClient.Builder okHttpClientBuilder = (new OkHttpClient.Builder()).callTimeout(serviceBuilder.callTimeout).connectTimeout(serviceBuilder.connectTimeout).readTimeout(serviceBuilder.readTimeout).writeTimeout(serviceBuilder.writeTimeout);
    OkHttpClient.Builder okHttpClientBuilder = (new OkHttpClient.Builder());
    okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
    okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());

    Retrofit retrofit = new Retrofit.Builder()
      .baseUrl("https://api.openai.com/v1/")
      .addConverterFactory(GsonConverterFactory.create())
      .build();

    OpenAiDalleApi service = retrofit.create(OpenAiDalleApi.class);

    Call<Image> call = service.generateImage(ImageRequest.builder().prompt(message.text()).size("256x256").build());

    try {
      retrofit2.Response<Image> response = call.execute();
//      return new Response<>(
//        AiMessage.from(response.body().get("data").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString())
//      );
      return new Response<>(response.body());
    } catch (IOException e) {
      e.printStackTrace();
      return null; // Handle error appropriately
    }
  }

//  @Override
//  public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
//    return null;
//  }
//
//  @Override
//  public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
//    return null;
//  }
}
