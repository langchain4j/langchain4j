package dev.langchain4j.store.embedding.vearch;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;

import java.util.ArrayList;
import java.util.List;

public class DeleteSpaceLastOrderer implements MethodOrderer {
    @Override
    public void orderMethods(MethodOrdererContext methodOrdererContext) {
        // should equal to VearchEmbeddingStoreIT#should_delete_space test name
        String deleteSpaceTestName = "should_delete_space";
        List<String> methodNames = new ArrayList<>();
        methodOrdererContext.getMethodDescriptors().forEach(methodDescriptor -> methodNames.add(methodDescriptor.getMethod().getName()));
        methodNames.sort((methodName1, methodName2) -> {
            //
            if (methodName1.equals(deleteSpaceTestName)) {
                return 1;
            } else if (methodName2.equals(deleteSpaceTestName)) {
                return -1;
            } else {
                return 0;
            }
        });
        methodOrdererContext.getMethodDescriptors().sort((md1, md2) -> {
            int index1 = methodNames.indexOf(md1.getMethod().getName());
            int index2 = methodNames.indexOf(md2.getMethod().getName());
            return Integer.compare(index1, index2);
        });
    }
}
