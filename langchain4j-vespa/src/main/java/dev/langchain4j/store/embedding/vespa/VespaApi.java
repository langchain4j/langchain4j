package dev.langchain4j.store.embedding.vespa;

import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

interface VespaApi {
    @GET("search/{query}")
    Call<QueryResponse> search(@Path(value = "query", encoded = true) String query);

    @DELETE("document/v1/{ns}/{docType}/docid?selection=true")
    Call<DeleteResponse> deleteAll(
            @Path("ns") String namespace, @Path("docType") String documentType, @Query("cluster") String clusterName);
}
