package dev.langchain4j.model.anthropic;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface AnthropicApi {

    @POST("messages")
    @Headers({"content-type: application/json"})
    Call<AnthropicChatResponse> chatCompletion(@Header("anthropic-version") String anthropicVersion,
                                               @Header("x-api-key") String apiKey,
                                               @Body AnthropicChatRequest request);
}
