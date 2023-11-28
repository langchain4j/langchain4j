package dev.langchain4j.model.workerai;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.workerai.client.AbstractWorkerAIModel;
import dev.langchain4j.model.workerai.client.WorkerAiImageGenerationRequest;
import dev.langchain4j.model.workerai.model.ImageModel;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * WorkerAI Image model.
 */
public class WorkerAiImageModel extends AbstractWorkerAIModel implements ImageModel {

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public WorkerAiImageModel(WorkerAiModelBuilder builder) {
        super(builder);
    }

    /** {@inheritDoc} */
    @Override
    public Response<byte[]> generate(String prompt) {
        return new Response<>(executeQuery(prompt), null, FinishReason.STOP);
    }

    /**
     * Execute query.
     *
     * @param prompt
     *     prompt.
     * @return
     *    image.
     */
    private byte[] executeQuery(String prompt) {
        try {
            // Mapping inbound
            WorkerAiImageGenerationRequest imgReq = new WorkerAiImageGenerationRequest();
            imgReq.setPrompt(prompt);

            retrofit2.Response<ResponseBody> response = workerAiClient
                    .generateImage(imgReq, accountIdentifier, modelName)
                    .execute();

            if (response.isSuccessful() && response.body() != null) {
                InputStream inputStream = response.body().byteStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                return buffer.toByteArray();
            }
            throw new IllegalStateException("Body is empty");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Response<File> generate(String prompt, String destinationFile) {
        try {
            byte[] image = executeQuery(prompt);
            try (FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
                fileOutputStream.write(image);
            }
            return new Response<>(new File(destinationFile), null, FinishReason.STOP);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}


