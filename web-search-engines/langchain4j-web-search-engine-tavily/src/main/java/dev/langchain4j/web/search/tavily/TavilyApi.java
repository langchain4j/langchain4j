package dev.langchain4j.web.search.tavily;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

interface TavilyApi {

    @POST("search")
    @Headers({"Content-Type: application/json", "User-Agent: langchain4j-web-search-engine-tavily"})
    Call<TavilyResponse> search(@Body TavilySearchRequest request);
}

