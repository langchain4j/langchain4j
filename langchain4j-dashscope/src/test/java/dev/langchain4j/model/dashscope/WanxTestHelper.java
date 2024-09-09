package dev.langchain4j.model.dashscope;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

public class WanxTestHelper {
    public static Stream<Arguments> imageModelNameProvider() {
        return Stream.of(
                Arguments.of(WanxModelName.WANX_V1)
        );
    }
}
