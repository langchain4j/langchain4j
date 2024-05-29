package dev.langchain4j.data.document.parser.llamaparse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;

@Slf4j
public class LlamaParseClient {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final LlmaParseApi llmaParseApi;
    private String apiKey;

    @Builder
    public LlamaParseClient(String baseUrl,
                            Duration timeout,
                            String apiKey) {
        this.apiKey = apiKey;
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .addInterceptor(new ApiKeyInsertingInterceptor(apiKey))
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout);

        OkHttpClient okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();

        llmaParseApi = retrofit.create(LlmaParseApi.class);
    }


    public LlamaParseResponse upload(Path path, String parsingInstructions) {
        try {
            RequestBody requestFile = RequestBody
                    .create(MediaType.parse("application/pdf"), path.toFile());

            MultipartBody.Part filePart =
                    MultipartBody.Part
                            .createFormData("file", path.toFile().getName(), requestFile);

            MultipartBody.Part parsingInstructionsPart =
                    MultipartBody.Part
                            .createFormData("parsingInstructions", parsingInstructions);

            retrofit2.Response<LlamaParseResponse> retrofitResponse
                    = llmaParseApi.upload(filePart, parsingInstructionsPart).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (Exception ex) {
            log.error("Error uploading file");
            throw new RuntimeException(ex);
        }
    }

    public ResponseBody jsonResult(String jobId) {
        try {
            retrofit2.Response<ResponseBody> response =
                    llmaParseApi.jsonResult(jobId).execute();
            if (response != null && response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            log.error("Error getting json result from the job {}", jobId);
            throw new RuntimeException(e);
        }
    }

    public LlamaParseMarkdownResponse markdownResult(String jobId) {
        try {
            retrofit2.Response<LlamaParseMarkdownResponse> response =
                 llmaParseApi.markdownResult(jobId).execute();
            if (response != null && response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            log.error("Error getting markdown result from the job {}", jobId);
            throw new RuntimeException(e);
        }
    }

    public LlamaParseTextResponse textResult(String jobId) {
        try {
            retrofit2.Response<LlamaParseTextResponse> response =
                    llmaParseApi.textResult(jobId).execute();
            if (response != null && response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            log.error("Error getting text result from the job {}", jobId);
            throw new RuntimeException(e);
        }
    }

    public ResponseBody imageResult(String jobId, String imageName) {
        try {
            retrofit2.Response<ResponseBody> response =
                    llmaParseApi.imageResult(jobId, imageName).execute();
            if (response != null && response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            log.error("Error getting image result from the job {}", jobId);
            throw new RuntimeException(e);
        }
    }

    public LlamaParseResponse jobStatus(String jobId)  {
        try {
            retrofit2.Response<LlamaParseResponse> retrofitResponse =
                    llmaParseApi.jobStatus(jobId).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }

        } catch (Exception ex) {
            log.error("Error getting job {} status", jobId);
            throw new RuntimeException(ex);
        }
    }

    private RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();

        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
