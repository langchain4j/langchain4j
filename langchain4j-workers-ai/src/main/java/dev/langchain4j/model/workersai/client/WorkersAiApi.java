package dev.langchain4j.model.workersai.client;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Public interface to interact with the WorkerAI API.
 */
public interface WorkersAiApi {

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
    Call<WorkersAiChatCompletionResponse> generateChat(@Body WorkersAiChatCompletionRequest apiRequest,
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
    Call<WorkersAiTextCompletionResponse> generateText(@Body WorkersAiTextCompletionRequest apiRequest,
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
    Call<ResponseBody> generateImage(@Body WorkersAiImageGenerationRequest apiRequest,
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
    Call<WorkersAiEmbeddingResponse>  embed(@Body WorkersAiEmbeddingRequest apiRequest,
             @Path("accountIdentifier") String accountIdentifier,
             @Path(value = "modelName", encoded = true) String modelName);

}
