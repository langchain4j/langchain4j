package dev.langchain4j.model.sparkdesk;

import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChatCompletionRequest;
import dev.langchain4j.model.sparkdesk.client.chat.http.HttpChatCompletionResponse;
import dev.langchain4j.model.sparkdesk.client.embedding.EmbeddingRequest;
import dev.langchain4j.model.sparkdesk.client.embedding.EmbeddingResponse;
import dev.langchain4j.model.sparkdesk.client.image.ImageRequest;
import dev.langchain4j.model.sparkdesk.client.image.ImageResponse;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface SparkdeskAiApi {

    @POST("v1/chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<HttpChatCompletionResponse> chatCompletion(@Body HttpChatCompletionRequest request);

    @POST("v1/chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<ResponseBody> streamChatCompletion(@Body HttpChatCompletionRequest request);

    @POST("/")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embeddings(@Body EmbeddingRequest request);

    @POST("v2.1/tti")
    @Headers({"Content-Type: application/json"})
    Call<ImageResponse> generations(@Body ImageRequest request);
}
