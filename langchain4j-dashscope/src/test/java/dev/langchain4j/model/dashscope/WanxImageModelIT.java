package dev.langchain4j.model.dashscope;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

import static dev.langchain4j.model.dashscope.QwenTestHelper.apiKey;
import static dev.langchain4j.model.dashscope.QwenTestHelper.multimodalImageData;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class WanxImageModelIT {
    Logger log = LoggerFactory.getLogger(WanxImageModelIT.class);

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.WanxTestHelper#imageModelNameProvider")
    void simple_image_generation_works(String modelName) {
        WanxImageModel model = WanxImageModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Response<Image> response = model.generate("Beautiful house on country side");

        URI remoteImage = response.content().url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.WanxTestHelper#imageModelNameProvider")
    void simple_image_edition_works_by_url(String modelName) {
        WanxImageModel model = WanxImageModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Image image = Image.builder()
                .url("https://img.alicdn.com/imgextra/i4/O1CN01K1DWat25own2MuQgF_!!6000000007574-0-tps-128-128.jpg")
                .build();

        Response<Image> response = model.edit(image, "Change the parrot's feathers with yellow");

        URI remoteImage = response.content().url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.model.dashscope.WanxTestHelper#imageModelNameProvider")
    void simple_image_edition_works_by_data(String modelName) {
        WanxImageModel model = WanxImageModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .build();

        Image image = Image.builder()
                .base64Data(multimodalImageData())
                .mimeType("image/jpg")
                .build();

        Response<Image> response = model.edit(image, "Change the parrot's feathers with yellow");

        URI remoteImage = response.content().url();
        log.info("Your remote image is here: {}", remoteImage);
        assertThat(remoteImage).isNotNull();
    }
}
