package dev.langchain4j.model.workerai.client;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Public interface to interact with the WorkerAI API.
 */
public interface WorkerAiApi {

    /**
     * Generate chat.
     *
     * @param apiRequest
     *      request.
     * @param accountIdentifier
     *      account identifier.
     * @param modelId
     *      model id.
     * @return
     *      response.
     */
    @POST("client/v4/accounts/{accountIdentifier}/ai/run/{modelName}")
    Call<WorkerAiChatCompletionResponse> generateChat(@Body WorkerAiChatCompletionRequest apiRequest,
                                                      @Path("accountIdentifier") String accountIdentifier,
                                                      @Path(value = "modelName", encoded = true) String modelId);

    /**
     * Generate text.
     *
     * @param apiRequest
     *      request.
     * @param accountIdentifier
     *      account identifier.
     * @param modelName
     *      model name.
     * @return
     *      response.
     */
    @POST("client/v4/accounts/{accountIdentifier}/ai/run/{modelName}")
    Call<WorkerAiTextCompletionResponse> generateText(@Body WorkerAiTextCompletionRequest apiRequest,
                                                      @Path("accountIdentifier") String accountIdentifier,
                                                      @Path(value = "modelName", encoded = true) String modelName);

    /**
     * Generate image.
     *
     * @param apiRequest
     *      request.
     * @param accountIdentifier
     *      account identifier.
     * @param modelName
     *      model name.
     * @return
     *      response.
     */
    @POST("client/v4/accounts/{accountIdentifier}/ai/run/{modelName}")
    Call<ResponseBody> generateImage(@Body WorkerAiImageGenerationRequest apiRequest,
                                     @Path("accountIdentifier") String accountIdentifier,
                                     @Path(value = "modelName", encoded = true) String modelName);

    /**
     * Generate embeddings.
     *
     * @param apiRequest
     *      request.
     * @param accountIdentifier
     *      account identifier.
     * @param modelName
     *      model name.
     * @return
     *      response.
     */
    @POST("client/v4/accounts/{accountIdentifier}/ai/run/{modelName}")
    Call<WorkerAiEmbeddingResponse>  embed(@Body WorkerAiEmbeddingRequest apiRequest,
             @Path("accountIdentifier") String accountIdentifier,
             @Path(value = "modelName", encoded = true) String modelName);

}
