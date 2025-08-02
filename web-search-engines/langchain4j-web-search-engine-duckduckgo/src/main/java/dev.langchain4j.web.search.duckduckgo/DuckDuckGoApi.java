package dev.langchain4j.web.search.duckduckgo;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

interface DuckDuckGoApi {

    @GET("/")
    Call<DuckDuckGoResponse> search(@Query("q") String query,
                                    @Query("format") String format,
                                    @Query("no_html") Integer noHtml,
                                    @Query("skip_disambig") Integer skipDisambig,
                                    @Query("safe_search") String safeSearch,
                                    @Query("region") String region);
}
