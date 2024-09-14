package dev.langchain4j.web.search.searxng;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.QueryMap;


interface SearXNGApi {
	@GET("search")
	@Headers({"Content-Type: application/json"})
	Call<SearXNGResults> search(@QueryMap Map<String, Object> params);
}