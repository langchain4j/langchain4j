package dev.langchain4j.model.anthropic;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface AnthropicApi {

    String X_API_KEY = "x-api-key";

    @POST("messages")
    @Headers({"content-type: application/json"})
    Call<AnthropicCreateMessageResponse> createMessage(@Header(X_API_KEY) String apiKey,
                                                       @Header("anthropic-version") String version,
                                                       @Body AnthropicCreateMessageRequest request);
}
