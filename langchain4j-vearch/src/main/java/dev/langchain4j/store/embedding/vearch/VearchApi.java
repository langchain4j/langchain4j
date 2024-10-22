package dev.langchain4j.store.embedding.vearch;

import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

interface VearchApi {

    int OK = 0;

    /* Database Operation */

    @GET("dbs")
    @Headers({"accept: application/json"})
    Call<ResponseWrapper<List<ListDatabaseResponse>>> listDatabase();

    @POST("dbs/{dbName}")
    @Headers({"accept: application/json", "content-type: application/json"})
    Call<ResponseWrapper<CreateDatabaseResponse>> createDatabase(@Path("dbName") String dbName);

    @GET("dbs/{dbName}/spaces")
    @Headers({"accept: application/json"})
    Call<ResponseWrapper<List<ListSpaceResponse>>> listSpaceOfDatabase(@Path("dbName") String dbName);

    /* Space (like a table in relational database) Operation */

    @POST("dbs/{dbName}/spaces")
    @Headers({"accept: application/json", "content-type: application/json"})
    Call<ResponseWrapper<CreateSpaceResponse>> createSpace(@Path("dbName") String dbName,
                                                           @Body CreateSpaceRequest request);

    @DELETE("dbs/{dbName}/spaces/{spaceName}")
    Call<Void> deleteSpace(@Path("dbName") String dbName, @Path("spaceName") String spaceName);

    /* Document Operation */

    @POST("document/upsert")
    @Headers({"accept: application/json", "content-type: application/json"})
    Call<ResponseWrapper<UpsertResponse>> upsert(@Body UpsertRequest request);

    @POST("document/search")
    @Headers({"accept: application/json", "content-type: application/json"})
    Call<ResponseWrapper<SearchResponse>> search(@Body SearchRequest request);
}
