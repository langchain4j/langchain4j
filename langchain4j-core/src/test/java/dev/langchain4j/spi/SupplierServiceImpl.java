package dev.langchain4j.spi;

public class SupplierServiceImpl implements SupplierService {
    @Override
    public String get() {
        return "Hello world!";
    }
}
