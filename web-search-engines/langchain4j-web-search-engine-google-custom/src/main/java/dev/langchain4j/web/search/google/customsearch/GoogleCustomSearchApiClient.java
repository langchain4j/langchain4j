package dev.langchain4j.web.search.google.customsearch;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.customsearch.v1.CustomSearchAPI;
import com.google.api.services.customsearch.v1.CustomSearchAPIRequest;
import com.google.api.services.customsearch.v1.model.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

class GoogleCustomSearchApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleCustomSearchApiClient.class);
    private static final Integer MAXIMUM_VALUE_NUM = 10;

    private final CustomSearchAPIRequest<Search> customSearchRequest;
    private final boolean logResponses;

    GoogleCustomSearchApiClient(String apiKey,
                                String csi,
                                Boolean siteRestrict,
                                Duration timeout,
                                Integer maxRetries,
                                boolean logRequests,
                                boolean logResponses) {

        try {
            if (isNullOrBlank(apiKey)) {
                throw new IllegalArgumentException("Google Custom Search API Key must be defined. " +
                        "It can be generated here: https://console.developers.google.com/apis/credentials");
            }
            if (isNullOrBlank(csi)) {
                throw new IllegalArgumentException("Google Custom Search Engine ID must be defined. " +
                        "It can be created here: https://cse.google.com/cse/create/new");
            }

            this.logResponses = logResponses;

            CustomSearchAPI.Builder customSearchAPIBuilder = new CustomSearchAPI.Builder(GoogleNetHttpTransport.newTrustedTransport(), new GsonFactory(), new HttpRequestInitializer() {
                @Override
                public void initialize(HttpRequest httpRequest) {
                    httpRequest.setConnectTimeout(Math.toIntExact(timeout.toMillis()));
                    httpRequest.setReadTimeout(Math.toIntExact(timeout.toMillis()));
                    httpRequest.setWriteTimeout(Math.toIntExact(timeout.toMillis()));
                    httpRequest.setNumberOfRetries(maxRetries);
                    if (logRequests) {
                        httpRequest.setInterceptor(new GoogleSearchApiHttpRequestLoggingInterceptor());
                    }
                    if (logResponses) {
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
        } catch (IOException e) {
            LOGGER.error("Error occurred while creating Google Custom Search API client", e);
            throw new RuntimeException(e);
        } catch (GeneralSecurityException e) {
            LOGGER.error("Error occurred while creating Google Custom Search API client using GoogleNetHttpTransport.newTrustedTransport()", e);
            throw new RuntimeException(e);
        }
    }

    public static GoogleCustomSearchApiClientBuilder builder() {
        return new GoogleCustomSearchApiClientBuilder();
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
            if (logResponses) {
                logResponse(searchPerformed);
            }
            return searchPerformed;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void logResponse(Search search) {
        try {
            LOGGER.debug("Response:\n- body: {}", search.toPrettyString());
        } catch (IOException e) {
            LOGGER.warn("Error while logging response: {}", e.getMessage());
        }
    }

    private static Integer maxResultsAllowed(Integer maxResults) {
        return maxResults > MAXIMUM_VALUE_NUM ? MAXIMUM_VALUE_NUM : maxResults;
    }

    private static Integer getDefaultNaturalNumber(Integer number) {
        int defaultNumber = getOrDefault(number, 1);
        return defaultNumber > 0 ? defaultNumber : 1;
    }

    private static Integer calculateIndexStartPage(Integer pageNumber, Integer index) {
        int indexStartPage = ((pageNumber - 1) * MAXIMUM_VALUE_NUM) + 1;
        return indexStartPage >= index ? indexStartPage : index;
    }

    public static class GoogleCustomSearchApiClientBuilder {
        private String apiKey;
        private String csi;
        private Boolean siteRestrict;
        private Duration timeout;
        private Integer maxRetries;
        private boolean logRequests;
        private boolean logResponses;

        GoogleCustomSearchApiClientBuilder() {
        }

        public GoogleCustomSearchApiClientBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public GoogleCustomSearchApiClientBuilder csi(String csi) {
            this.csi = csi;
            return this;
        }

        public GoogleCustomSearchApiClientBuilder siteRestrict(Boolean siteRestrict) {
            this.siteRestrict = siteRestrict;
            return this;
        }

        public GoogleCustomSearchApiClientBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public GoogleCustomSearchApiClientBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public GoogleCustomSearchApiClientBuilder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public GoogleCustomSearchApiClientBuilder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public GoogleCustomSearchApiClient build() {
            return new GoogleCustomSearchApiClient(this.apiKey, this.csi, this.siteRestrict, this.timeout, this.maxRetries, this.logRequests, this.logResponses);
        }

        public String toString() {
            return "GoogleCustomSearchApiClient.GoogleCustomSearchApiClientBuilder(apiKey=" + this.apiKey + ", csi=" + this.csi + ", siteRestrict=" + this.siteRestrict + ", timeout=" + this.timeout + ", maxRetries=" + this.maxRetries + ", logRequests=" + this.logRequests + ", logResponses=" + this.logResponses + ")";
        }
    }
}
