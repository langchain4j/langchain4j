package dev.langchain4j.model.workersai;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

@EnabledIfEnvironmentVariable(named = "WORKERS_AI_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "WORKERS_AI_ACCOUNT_ID", matches = ".*")
class WorkersAiImageModelIT {

    static WorkersAiImageModel imageModel;

    @BeforeAll
    static void initializeModel() {
        imageModel = WorkersAiImageModel.builder()
                .modelName(WorkersAiImageModelName.STABLE_DIFFUSION_XL.toString())
                .accountId(System.getenv("WORKERS_AI_ACCOUNT_ID"))
                .apiToken(System.getenv("WORKERS_AI_API_KEY"))
                .build();
    }

    @Test
    void should_generate_an_image_as_binary() {
        Response<Image> image = imageModel.generate("Draw me a squirrel");;
        Assertions.assertNotNull(image.content());
        Assertions.assertNotNull(image.content().base64Data());
    }

    @Test
    void should_generate_an_image_as_file() {
        String homeDirectory = System.getProperty("user.home");
        Response<File> image = imageModel.generate("Draw me a squirrel",
                System.getProperty("user.home") + "/langchain4j-squirrel.png");;
        Assertions.assertTrue(image.content().exists());
    }

    @Test
    void should_edit_source_image() throws Exception {
        Image sourceImage  = imageModel
                .convertAsImage(
                        getImageFromUrl("https://pub-1fb693cb11cc46b2b2f656f51e015a2c.r2.dev/dog.png"));
        Image maskImage = imageModel
                .convertAsImage(
                        getImageFromUrl( "https://pub-1fb693cb11cc46b2b2f656f51e015a2c.r2.dev/dog.png"));
        Response<Image> image = imageModel.edit(sourceImage, maskImage, "Face of a yellow cat, high resolution, sitting on a park bench");
        saveOutputToFile(Base64.getDecoder().decode(image.content().base64Data()),
                System.getProperty("user.home") + "/Downloads/yellow_cat_on_park_bench.png");
    }

    private byte[] getImageFromUrl(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoInput(true);
        connection.connect();
        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    private void saveOutputToFile(byte[] image, String destinationFile) throws Exception {
        try (FileOutputStream fileOutputStream = new FileOutputStream(destinationFile)) {
            fileOutputStream.write(image);
        }
    }

}
