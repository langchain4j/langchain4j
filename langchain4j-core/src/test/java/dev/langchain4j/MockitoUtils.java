package dev.langchain4j;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

public class MockitoUtils {

    public static <T> T ignoreInteractions(T mock) {
        return verify(mock, atLeast(0));
    }
}
