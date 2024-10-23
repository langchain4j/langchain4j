package dev.langchain4j.store.embedding.vespa;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

interface VespaQueryApi {

  @GET("search/{query}")
  Call<QueryResponse> search(@Path(value = "query", encoded = true) String query);
}
