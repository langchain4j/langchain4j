package dev.langchain4j.model.anthropic;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

interface AnthropicApi {

    String X_API_KEY = "x-api-key";

    @POST("messages")
    @Headers({"content-type: application/json"})
    Call<AnthropicCreateMessageResponse> createMessage(@Header(X_API_KEY) String apiKey,
                                                       @Header("anthropic-version") String version,
                                                       @Body AnthropicCreateMessageRequest request);

    @Streaming
    @POST("messages")
    @Headers({"content-type: application/json"})
    Call<ResponseBody> streamMessage(@Header(X_API_KEY) String apiKey,
                                     @Header("anthropic-version") String version,
                                     @Body AnthropicCreateMessageRequest request);
}
