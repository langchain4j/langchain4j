package dev.langchain4j.tool.web.search.google.customsearch;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.customsearch.v1.CustomSearchAPI;
import com.google.api.services.customsearch.v1.CustomSearchAPIRequest;
import com.google.api.services.customsearch.v1.model.Result;
import com.google.api.services.customsearch.v1.model.Search;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

class GoogleCustomSearchApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleCustomSearchApiClient.class);
    private final CustomSearchAPIRequest<Search> customSearchRequest;
    private final Boolean logRequestResponse;

    @Builder
    GoogleCustomSearchApiClient(String apiKey,
                                String csi,
                                Boolean siteRestrict,
                                Duration timeout,
                                Integer maxRetries,
                                Boolean logRequestResponse) {

        try {
            if (isNullOrBlank(apiKey)) {
                throw new IllegalArgumentException("Google Custom Search API Key must be defined. " +
                        "It can be generated here: https://console.developers.google.com/apis/credentials");
            }
            if (isNullOrBlank(csi)) {
                throw new IllegalArgumentException("Google Custom Search Engine ID must be defined. " +
                        "It can be created here: https://cse.google.com/cse/create/new");
            }

            CustomSearchAPI.Builder customSearchAPIBuilder = new CustomSearchAPI.Builder(new NetHttpTransport(), new GsonFactory(), new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest httpRequest) throws IOException {
                    httpRequest.setConnectTimeout(Math.toIntExact(timeout.toMillis()));
                    httpRequest.setReadTimeout(Math.toIntExact(timeout.toMillis()));
                    httpRequest.setWriteTimeout(Math.toIntExact(timeout.toMillis()));
                    httpRequest.setLoggingEnabled(logRequestResponse);
                    httpRequest.setNumberOfRetries(maxRetries);
                    if (logRequestResponse){
                        httpRequest.setInterceptor(new GoogleSearchApiHttpRequestLoggingInterceptor());
                        httpRequest.setResponseInterceptor(new GoogleSearchApiHttpResponseLoggingInterceptor());
                    }
                }
            }).setApplicationName("LangChain4j");

            CustomSearchAPI customSearchAPI = customSearchAPIBuilder.build();

            if (siteRestrict) {
                customSearchRequest = customSearchAPI.cse().siterestrict().list().setKey(apiKey).setCx(csi);
            } else {
                customSearchRequest = customSearchAPI.cse().list().setKey(apiKey).setCx(csi);
            }
            this.logRequestResponse = logRequestResponse;
        } catch (IOException e) {
            LOGGER.error("Error occurred while creating Google Custom Search API client", e);
            throw new RuntimeException(e);
        }
    }

    List<Result> searchResults(Search.Queries.Request requestQuery) {
        try {
            List<Result> results;
            if (customSearchRequest instanceof CustomSearchAPI.Cse.Siterestrict.List) {
                 results = ((CustomSearchAPI.Cse.Siterestrict.List) customSearchRequest)
                        .setPrettyPrint(true)
                        .setQ(requestQuery.getExactTerms())
                        .setNum(requestQuery.getCount())
                        .setSort(requestQuery.getSort())
                        .setSafe(requestQuery.getSafe())
                        .setDateRestrict(requestQuery.getDateRestrict())
                        .setGl(requestQuery.getGl())
                        .setLr(requestQuery.getLanguage())
                        .setStart(requestQuery.getStartIndex().longValue())
                        .execute().getItems();
            } else if (customSearchRequest instanceof CustomSearchAPI.Cse.List) {
                results = ((CustomSearchAPI.Cse.List) customSearchRequest)
                        .setPrettyPrint(true)
                        .setQ(requestQuery.getExactTerms())
                        .setNum(requestQuery.getCount())
                        .setSort(requestQuery.getSort())
                        .setSafe(requestQuery.getSafe())
                        .setDateRestrict(requestQuery.getDateRestrict())
                        .setGl(requestQuery.getGl())
                        .setLr(requestQuery.getLanguage())
                        .setStart(requestQuery.getStartIndex().longValue())
                        .execute().getItems();
            } else {
                throw new IllegalStateException("Invalid CustomSearchAPIRequest type");
            }
            if (logRequestResponse) {
                results.forEach(GoogleCustomSearchApiClient::log);
            }
            return results;
        } catch (IOException e) {
            LOGGER.error("Error occurred while searching", e);
            throw new RuntimeException(e);
        }
    }

    private static void log(Result result){
        try {
            LOGGER.debug("Response:\n- body: {}", result.toPrettyString());
        } catch (IOException e) {
            LOGGER.warn("Error while logging response: {}", e.getMessage());
        }
    }
}
