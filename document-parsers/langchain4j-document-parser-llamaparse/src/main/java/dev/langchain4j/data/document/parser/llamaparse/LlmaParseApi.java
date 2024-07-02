package dev.langchain4j.data.document.parser.llamaparse;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

interface LlmaParseApi {

    enum JOB_STATUS {
        PENDING("PENDING"),
        SUCCESS("SUCCESS"),
        ERROR("ERROR"),
        CANCELED("CANCELED");

        private String value;
        JOB_STATUS(String value) {
            this.value = value;
        }

        String value() {
            return this.value;
        }
    }

    final String baseUrl = "https://api.cloud.llamaindex.ai/api/parsing/";

    @POST("upload")
    @Multipart
    @Headers({"accept: application/json", "accept: application/json"})
    Call<LlamaParseResponse> upload(@Part MultipartBody.Part part, @Part MultipartBody.Part parsingInstructions);

    @GET("job/{job_id}/result/json")
    @Headers({"accept: application/json"})
    Call<ResponseBody> jsonResult(@Path("job_id") String job_id);

    @GET("job/{job_id}/result/markdown")
    @Headers({"accept: application/json"})
    Call<LlamaParseMarkdownResponse> markdownResult(@Path("job_id") String job_id);

    @GET("job/{job_id}/result/text")
    @Headers({"accept: application/json"})
    Call<LlamaParseTextResponse> textResult(@Path("job_id") String job_id);

    @GET("job/{job_id}/result/image/{image_name}")
    @Headers({"accept: application/json"})
    Call<ResponseBody> imageResult(@Path("job_id") String job_id, @Path("image_name") String image_name);

    @GET("job/{job_id}")
    @Headers({"accept: application/json"})
    Call<LlamaParseResponse> jobStatus(@Path("job_id") String job_id);
}
