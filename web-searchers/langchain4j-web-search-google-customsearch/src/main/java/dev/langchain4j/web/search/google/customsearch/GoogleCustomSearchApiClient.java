package dev.langchain4j.web.search.google.customsearch;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.customsearch.v1.CustomSearchAPI;
import com.google.api.services.customsearch.v1.CustomSearchAPIRequest;
import com.google.api.services.customsearch.v1.model.Search;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

class GoogleCustomSearchApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleCustomSearchApiClient.class);
    private static final Integer MAXIMUM_VALUE_NUM = 10;
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

    Search searchResults(Search.Queries.Request requestQuery) {
        try {
            Search searchPerformed;
            if (customSearchRequest instanceof CustomSearchAPI.Cse.Siterestrict.List) {
                searchPerformed = ((CustomSearchAPI.Cse.Siterestrict.List) customSearchRequest)
                        .setPrettyPrint(true)
                        .setQ(requestQuery.getSearchTerms())
                        .setNum(maxResultsAllowed(getDefaultNaturalNumber(requestQuery.getCount())))
                        .setSort(requestQuery.getSort())
                        .setSafe(requestQuery.getSafe())
                        .setDateRestrict(requestQuery.getDateRestrict())
                        .setGl(requestQuery.getGl())
                        .setLr(requestQuery.getLanguage())
                        .setHl(requestQuery.getHl())
                        .setHq(requestQuery.getHq())
                        .setSiteSearch(requestQuery.getSiteSearch())
                        .setSiteSearchFilter(requestQuery.getSiteSearchFilter())
                        .setExactTerms(requestQuery.getExactTerms())
                        .setExcludeTerms(requestQuery.getExcludeTerms())
                        .setLinkSite(requestQuery.getLinkSite())
                        .setOrTerms(requestQuery.getOrTerms())
                        .setLowRange(requestQuery.getLowRange())
                        .setHighRange(requestQuery.getHighRange())
                        .setSearchType(requestQuery.getSearchType())
                        .setFileType(requestQuery.getFileType())
                        .setRights(requestQuery.getRights())
                        .setImgSize(requestQuery.getImgSize())
                        .setImgType(requestQuery.getImgType())
                        .setImgColorType(requestQuery.getImgColorType())
                        .setImgDominantColor(requestQuery.getImgDominantColor())
                        .setC2coff(requestQuery.getDisableCnTwTranslation())
                        .setCr(requestQuery.getCr())
                        .setGooglehost(requestQuery.getGoogleHost())
                        .setStart(calculateIndexStartPage(
                                getDefaultNaturalNumber(requestQuery.getStartPage()),
                                getDefaultNaturalNumber(requestQuery.getStartIndex())
                        ).longValue())
                        .setFilter(requestQuery.getFilter())
                        .execute();
            } else if (customSearchRequest instanceof CustomSearchAPI.Cse.List) {
                searchPerformed = ((CustomSearchAPI.Cse.List) customSearchRequest)
                        .setPrettyPrint(true)
                        .setQ(requestQuery.getSearchTerms())
                        .setNum(maxResultsAllowed(getDefaultNaturalNumber(requestQuery.getCount())))
                        .setSort(requestQuery.getSort())
                        .setSafe(requestQuery.getSafe())
                        .setDateRestrict(requestQuery.getDateRestrict())
                        .setGl(requestQuery.getGl())
                        .setLr(requestQuery.getLanguage())
                        .setHl(requestQuery.getHl())
                        .setHq(requestQuery.getHq())
                        .setSiteSearch(requestQuery.getSiteSearch())
                        .setSiteSearchFilter(requestQuery.getSiteSearchFilter())
                        .setExactTerms(requestQuery.getExactTerms())
                        .setExcludeTerms(requestQuery.getExcludeTerms())
                        .setLinkSite(requestQuery.getLinkSite())
                        .setOrTerms(requestQuery.getOrTerms())
                        .setLowRange(requestQuery.getLowRange())
                        .setHighRange(requestQuery.getHighRange())
                        .setSearchType(requestQuery.getSearchType())
                        .setFileType(requestQuery.getFileType())
                        .setRights(requestQuery.getRights())
                        .setImgSize(requestQuery.getImgSize())
                        .setImgType(requestQuery.getImgType())
                        .setImgColorType(requestQuery.getImgColorType())
                        .setImgDominantColor(requestQuery.getImgDominantColor())
                        .setC2coff(requestQuery.getDisableCnTwTranslation())
                        .setCr(requestQuery.getCr())
                        .setGooglehost(requestQuery.getGoogleHost())
                        .setStart(calculateIndexStartPage(
                                getDefaultNaturalNumber(requestQuery.getStartPage()),
                                getDefaultNaturalNumber(requestQuery.getStartIndex())
                                ).longValue())
                        .setFilter(requestQuery.getFilter())
                        .execute();
            } else {
                throw new IllegalStateException("Invalid CustomSearchAPIRequest type");
            }
            if (logRequestResponse) {
                log(searchPerformed);
            }
            return searchPerformed;
        } catch (IOException e) {
            LOGGER.error("Error occurred while searching", e);
            throw new RuntimeException(e);
        }
    }

    private static void log(Search search){
        try {
            LOGGER.debug("Response:\n- Response: {}", search.toPrettyString());
        } catch (IOException e) {
            LOGGER.warn("Error while logging response: {}", e.getMessage());
        }
    }

    private static Integer maxResultsAllowed(Integer maxResults){
        return maxResults > MAXIMUM_VALUE_NUM ? MAXIMUM_VALUE_NUM : maxResults;
    }

    private static Integer getDefaultNaturalNumber(Integer number){
        int defaultNumber = getOrDefault(number, 1);
        return defaultNumber > 0 ? defaultNumber : 1;
    }

    private static Integer calculateIndexStartPage(Integer pageNumber, Integer index) {
        int indexStartPage = ((pageNumber -1) * MAXIMUM_VALUE_NUM) + 1;
        return indexStartPage >= index ? indexStartPage : index;
    }
}
