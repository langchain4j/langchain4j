package dev.langchain4j.store.embedding.vearch;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface VearchApi {

    int OK = 200;

    /* Database Operation */

    @GET("/list/db")
    Call<ResponseWrapper<List<ListDatabaseResponse>>> listDatabase();

    @PUT("/db/_create")
    Call<ResponseWrapper<CreateDatabaseResponse>> createDatabase(@Body CreateDatabaseRequest request);

    @GET("/list/space")
    Call<ResponseWrapper<List<ListSpaceResponse>>> listSpaceOfDatabase(@Query("db") String dbName);

    /* Space (like a table in relational database) Operation */

    @PUT("/space/{db}/_create")
    Call<ResponseWrapper<CreateSpaceResponse>> createSpace(@Path("db") String dbName,
                                                           @Body CreateSpaceRequest request);

    /* Document Operation */

    @POST("/{db}/{space}/_bulk")
    Call<List<BulkResponse>> bulk(@Path("db") String db,
                                  @Path("space") String space,
                                  @Body RequestBody requestBody);

    @POST("/{db}/{space}/_search")
    Call<SearchResponse> search(@Path("db") String db,
                                @Path("space") String space,
                                @Body SearchRequest request);

    @DELETE("/space/{db}/{space}")
    Call<Void> deleteSpace(@Path("db") String dbName, @Path("space") String spaceName);
}
