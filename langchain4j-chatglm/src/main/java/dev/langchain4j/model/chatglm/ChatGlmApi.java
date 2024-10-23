package dev.langchain4j.model.chatglm;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface ChatGlmApi {

    int OK = 200;

    @POST(".")
    @Headers({"Content-Type: application/json"})
    Call<ChatCompletionResponse> chatCompletion(@Body ChatCompletionRequest chatCompletionRequest);
}
