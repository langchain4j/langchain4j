package dev.langchain4j.store.embedding.vearch.api;

import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface VearchApi {

    /* Database Operation */

    @GET("/list/db")
    Call<ResponseWrapper<List<ListDatabaseResponse>>> listDatabase();

    @PUT("_create")
    Call<ResponseWrapper<CreateDatabaseResponse>> createDatabase(@Body CreateDatabaseRequest request);

    @GET("/list/space")
    Call<ResponseWrapper<List<ListSpaceResponse>>> viewSpaceOfDatabase(@Query("db_name") String dbName);

    /* Space (like a table in relational database) Operation */

    @POST("/space/{db_name}/_create")
    Call<ResponseWrapper<CreateSpaceResponse>> createSpace(@Path("db_name") String dbName,
                                                           @Body CreateSpaceRequest request);

    /* Document Operation */

    @POST("/document/upsert")
    Call<ResponseWrapper<InsertionResponse>> batchInsertion(InsertionRequest request);

    @POST("/document/search")
    Call<ResponseWrapper<SearchResponse>> search(@Body SearchRequest request);
}
