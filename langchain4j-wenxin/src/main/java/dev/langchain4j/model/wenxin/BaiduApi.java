package dev.langchain4j.model.wenxin;

import dev.langchain4j.model.wenxin.client.chat.ChatCompletionRequest;
import dev.langchain4j.model.wenxin.client.chat.ChatCompletionResponse;
import dev.langchain4j.model.wenxin.client.chat.ChatTokenResponse;
import dev.langchain4j.model.wenxin.client.completion.CompletionRequest;
import dev.langchain4j.model.wenxin.client.completion.CompletionResponse;
import dev.langchain4j.model.wenxin.client.embedding.EmbeddingRequest;
import dev.langchain4j.model.wenxin.client.embedding.EmbeddingResponse;
import retrofit2.Call;
import retrofit2.http.*;

public interface BaiduApi {

        @POST("rpc/2.0/ai_custom/v1/wenxinworkshop/chat/{serviceName}")
        @Headers({"Content-Type: application/json"})
        Call<ChatCompletionResponse> chatCompletions(@Path(value = "serviceName",encoded = false) String serviceName,@Body ChatCompletionRequest var1, @Query("access_token") String accessToken);
        @POST("rpc/2.0/ai_custom/v1/wenxinworkshop/completions/{serviceName}")
        @Headers({"Content-Type: application/json"})
        Call<CompletionResponse> completions(@Path(value = "serviceName",encoded = false) String serviceName,@Body CompletionRequest var1, @Query("access_token") String accessToken);
        @POST("rpc/2.0/ai_custom/v1/wenxinworkshop/embeddings/{serviceName}")
        @Headers({"Content-Type: application/json"})
        Call<EmbeddingResponse> embeddings(@Path(value = "serviceName",encoded = false) String serviceName,@Body EmbeddingRequest var1,@Query("access_token") String accessToken);
        @GET("oauth/2.0/token")
        @Headers({"Content-Type: application/json"})
        Call<ChatTokenResponse> getToken(@Query("grant_type") String grantType,@Query("client_id") String clientId,@Query("client_secret") String clientSecret);

    }

