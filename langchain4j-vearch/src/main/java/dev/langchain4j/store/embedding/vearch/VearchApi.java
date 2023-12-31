package dev.langchain4j.store.embedding.vearch;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface VearchApi {

    @POST("/{db_name}/{space_name}/_bulk")
    Call<InsertionResponse> batchInsertion(@Path("db_name") String dbName,
                                           @Path("space_name") String spaceName,
                                           @Body InsertionRequest request);

    @POST("/{db_name}/{space_name}/_search")
    Call<SearchResponse> search(@Path("db_name") String dbName,
                                @Path("space_name") String spaceName,
                                @Body SearchRequest request);
}
