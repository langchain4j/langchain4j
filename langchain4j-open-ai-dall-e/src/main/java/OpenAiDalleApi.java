import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.image.ImageRequest;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface OpenAiDalleApi {
  @Headers({ "Content-Type: application/json" })
  @POST("images/generations")
  Call<Image> generateImage(@Body ImageRequest request);
}
