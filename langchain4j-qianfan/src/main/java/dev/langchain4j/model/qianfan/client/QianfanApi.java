package dev.langchain4j.model.qianfan.client;

import dev.langchain4j.model.qianfan.client.chat.ChatCompletionRequest;
import dev.langchain4j.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.model.qianfan.client.chat.ChatTokenResponse;
import dev.langchain4j.model.qianfan.client.completion.CompletionRequest;
import dev.langchain4j.model.qianfan.client.completion.CompletionResponse;
import dev.langchain4j.model.qianfan.client.embedding.EmbeddingRequest;
import dev.langchain4j.model.qianfan.client.embedding.EmbeddingResponse;
import retrofit2.Call;
import retrofit2.http.*;

interface QianfanApi {

        @POST("rpc/2.0/ai_custom/v1/wenxinworkshop/chat/{endpoint}")
        @Headers({"Content-Type: application/json"})
        Call<ChatCompletionResponse> chatCompletions(@Path(value = "endpoint", encoded = false) String endpoint, @Body ChatCompletionRequest request, @Query("access_token") String accessToken);

        @POST("rpc/2.0/ai_custom/v1/wenxinworkshop/completions/{endpoint}")
        @Headers({"Content-Type: application/json"})
        Call<CompletionResponse> completions(@Path(value = "endpoint", encoded = false) String endpoint, @Body CompletionRequest request, @Query("access_token") String accessToken);

        @POST("rpc/2.0/ai_custom/v1/wenxinworkshop/embeddings/{endpoint}")
        @Headers({"Content-Type: application/json"})
        Call<EmbeddingResponse> embeddings(@Path(value = "endpoint", encoded = false) String endpoint, @Body EmbeddingRequest request, @Query("access_token") String accessToken);

        @GET("oauth/2.0/token")
        @Headers({"Content-Type: application/json"})
        Call<ChatTokenResponse> getToken(@Query("grant_type") String grantType, @Query("client_id") String clientId, @Query("client_secret") String clientSecret);

    }

