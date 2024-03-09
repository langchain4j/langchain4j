package dev.langchain4j.model.workerai;

import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.File;

@Disabled("requires a worker ai account")
@EnabledIfEnvironmentVariable(named = "WORKERAI_API_KEY", matches = ".*")
@EnabledIfEnvironmentVariable(named = "WORKERAI_ACCOUNT_ID", matches = ".*")
public class WorkerAiImageModelIT {

    static WorkerAiImageModel imageModel;

    @BeforeAll
    public static void initializeModel() {
        imageModel = WorkerAiImageModel.builder()
                .modelName(WorkerAiModelName.STABLE_DIFFUSION_XL)
                .accountIdentifier(System.getenv("WORKERAI_ACCOUNT_ID"))
                .token(System.getenv("WORKERAI_API_KEY"))
                .buildImageModel();
    }

    @Test
    void should_generate_an_image_as_binary() {
        Response<byte[]> image = imageModel.generate("Draw me a squirrel");;
        Assertions.assertNotNull(image.content());
        Assertions.assertTrue(image.content().length > 0);
    }

    @Test
    void should_generate_an_image_as_file() {
        String homeDirectory = System.getProperty("user.home");
        Response<File> image = imageModel.generate("Draw me a squirrel",
                System.getProperty("user.home") + "/langchain4j-squirrel.png");;
        Assertions.assertTrue(image.content().exists());
    }

}
