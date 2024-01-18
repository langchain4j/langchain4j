package dev.langchain4j.store.embedding.vearch.api;

import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface VearchApi {

    int OK = 200;

    /* Database Operation */

    @GET("/list/db")
    Call<ResponseWrapper<List<ListDatabaseResponse>>> listDatabase();

    @PUT("_create")
    Call<ResponseWrapper<CreateDatabaseResponse>> createDatabase(@Body CreateDatabaseRequest request);

    @GET("/list/space")
    Call<ResponseWrapper<List<ListSpaceResponse>>> listSpaceOfDatabase(@Query("db_name") String dbName);

    /* Space (like a table in relational database) Operation */

    @POST("/space/{db_name}/_create")
    Call<ResponseWrapper<CreateSpaceResponse>> createSpace(@Path("db_name") String dbName,
                                                           @Body CreateSpaceRequest request);

    /* Document Operation */

    @POST("/document/upsert")
    Call<InsertionResponse> batchInsert(InsertionRequest request);

    @POST("/document/search")
    Call<SearchResponse> search(@Body SearchRequest request);
}
