package dev.langchain4j.model.zhipu;

import dev.langchain4j.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.model.zhipu.embedding.EmbeddingRequest;
import dev.langchain4j.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.model.zhipu.image.ImageRequest;
import dev.langchain4j.model.zhipu.image.ImageResponse;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

interface ZhipuAiApi {

    @POST("api/paas/v4/chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<ChatCompletionResponse> chatCompletion(@Body ChatCompletionRequest request);

    @Streaming
    @POST("api/paas/v4/chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<ResponseBody> streamingChatCompletion(@Body ChatCompletionRequest request);

    @POST("api/paas/v4/embeddings")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embeddings(@Body EmbeddingRequest request);

    @POST("api/paas/v4/images/generations")
    @Headers({"Content-Type: application/json"})
    Call<ImageResponse> generations(@Body ImageRequest request);
}
