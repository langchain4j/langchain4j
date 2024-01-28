package dev.langchain4j.model.zhipu;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

interface ZhipuAiApi {

    @POST("api/paas/v4/chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<ZhipuAiChatCompletionResponse> chatCompletion(@Body ZhipuAiChatCompletionRequest request);

    @Streaming
    @POST("api/paas/v4/chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<ResponseBody> streamingChatCompletion(@Body ZhipuAiChatCompletionRequest request);

    @POST("api/paas/v4/embeddings")
    @Headers({"Content-Type: application/json"})
    Call<ZhipuAiEmbeddingResponse> embeddings(@Body ZhipuAiEmbeddingRequest request);
}
