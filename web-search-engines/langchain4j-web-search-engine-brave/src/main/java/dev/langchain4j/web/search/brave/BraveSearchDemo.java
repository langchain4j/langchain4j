    package dev.langchain4j.web.search.brave;


    import dev.langchain4j.web.search.WebSearchResults;

    import java.io.UnsupportedEncodingException;




    import java.time.Duration;

    public class BraveSearchDemo {
        public static void main(String[] args) throws UnsupportedEncodingException {
            // Parameters for the BraveWebSearchEngine
            String baseUrl = "https://api.search.brave.com";
            String apiKey = ""; // Replace with your API key
            Duration timeout = Duration.ofSeconds(10);
            Integer count = 10;
            String safeSearch = "off";
            String resultFilter = "web";
            String freshness = "";

            // Create an instance of BraveWebSearchEngine
            BraveWebSearchEngine braveSearchEngine = new BraveWebSearchEngine(
                    baseUrl,
                    apiKey,
                    timeout,
                    count,
                    safeSearch,
                    resultFilter,
                    freshness
            );

            String query = "Places in hyderabad to visit ";
            String encodedQuery = query.replace(" ", "%20");
            System.out.println("Encoded query: " + encodedQuery);


            // Perform the search
            try {
                System.out.println("Searching for: " + encodedQuery);
                WebSearchResults results = braveSearchEngine.search(query);
                System.out.println("Search results: " + results);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to fetch search results.");
            }
        }
    }
