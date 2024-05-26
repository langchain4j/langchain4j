package dev.langchain4j.web.search.search_api;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

// TODO: improve this for various types of search API calls
interface  SearchApiApi {
    @GET("/search")
    Call<SearchApiResponse> search(@Query("q") String query);
}

