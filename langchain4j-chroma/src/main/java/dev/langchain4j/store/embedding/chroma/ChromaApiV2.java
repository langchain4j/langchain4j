package dev.langchain4j.store.embedding.chroma;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

interface ChromaApiV2 {

    @POST("api/v2/tenants")
    @Headers({"Content-Type: application/json"})
    Call<Void> createTenant(@Body CreateTenantRequest createTenantRequest);

    @GET("api/v2/tenants/{tenant_name}")
    @Headers({"Content-Type: application/json"})
    Call<Tenant> tenant(@Path("tenant_name") String tenantName);

    @POST("api/v2/tenants/{tenant_name}/databases")
    @Headers({"Content-Type: application/json"})
    Call<Void> createDatabase(
            @Path("tenant_name") String tenantName, @Body CreateDatabaseRequest createDatabaseRequest);

    @GET("api/v2/tenants/{tenant_name}/databases/{database_name}")
    @Headers({"Content-Type: application/json"})
    Call<Database> database(@Path("tenant_name") String tenantName, @Path("database_name") String databaseName);

    @GET("api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<Collection> collection(
            @Path("tenant_name") String tenantName,
            @Path("database_name") String databaseName,
            @Path("collection_name") String collectionName);

    @POST("api/v2/tenants/{tenant_name}/databases/{database_name}/collections")
    @Headers({"Content-Type: application/json"})
    Call<Collection> createCollection(
            @Path("tenant_name") String tenantName,
            @Path("database_name") String databaseName,
            @Body CreateCollectionRequest createCollectionRequest);

    @POST("api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_id}/add")
    @Headers({"Content-Type: application/json"})
    Call<Void> addEmbeddings(
            @Path("tenant_name") String tenantName,
            @Path("database_name") String databaseName,
            @Path("collection_id") String collectionId,
            @Body AddEmbeddingsRequest embedding);

    @POST("api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_id}/query")
    @Headers({"Content-Type: application/json"})
    Call<QueryResponse> queryCollection(
            @Path("tenant_name") String tenantName,
            @Path("database_name") String databaseName,
            @Path("collection_id") String collectionId,
            @Body QueryRequest queryRequest);

    @POST("api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_id}/delete")
    @Headers({"Content-Type: application/json"})
    Call<List<String>> deleteEmbeddings(
            @Path("tenant_name") String tenantName,
            @Path("database_name") String databaseName,
            @Path("collection_id") String collectionId,
            @Body DeleteEmbeddingsRequest embedding);

    @DELETE("api/v2/tenants/{tenant_name}/databases/{database_name}/collections/{collection_name}")
    @Headers({"Content-Type: application/json"})
    Call<Collection> deleteCollection(
            @Path("tenant_name") String tenantName,
            @Path("database_name") String databaseName,
            @Path("collection_name") String collectionName);
}
