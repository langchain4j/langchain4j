package dev.langchain4j.web.search.google.customsearch;


import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

public interface GoogleSearchApi implements SearchApi {

    public static final String GOOGlE_SEARCH_API_URL = "https://www.googleapis.com/customsearch/v1";

    private final HttpClient httpClient;

    public GoogleSearchApi(){
        this.httpClient = HttpClient.createDefault();
    }

    @Override
    public String
    search(String query){
        try{
            String apiKey = "<YOUR_API_KEY>";
            String cx = "<YOUR_CX>";

            String url = GOOGlE_SEARCH_API_URL + "?key=" + apiKey + "&cx=" + cx + "&q=" + query;

            HttpGet request = new HttpGet(url);
            HttpResponse response = httpClient.execute(request);
            return
                    EntityUtils.toString(response.getEntity());

        }catch (IOException e){
            e.printStackTrace();
            return null;
        }
    }

}

public class SearchApiExample {
    public static void main(String[] args){
        SeachApi seachApi = new GoogleSearchApi();
        String query = "OpenAI GPT-3.5";

        String result = seachApi.search(query);

        System.out.println("Search Result For ' " + query + " ':\n" + result);
    }
}