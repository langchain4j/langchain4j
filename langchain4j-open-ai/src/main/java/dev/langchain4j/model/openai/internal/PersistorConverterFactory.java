//package dev.langchain4j.model.openai.internal;
//
//import dev.langchain4j.model.openai.internal.image.GenerateImagesResponse;
//import okhttp3.ResponseBody;
//import retrofit2.Converter;
//import retrofit2.Retrofit;
//
//import java.io.IOException;
//import java.lang.annotation.Annotation;
//import java.lang.reflect.Type;
//import java.nio.file.Path;
//
//class PersistorConverterFactory extends Converter.Factory {
//
//    private final Path persistTo;
//
//    PersistorConverterFactory(Path persistTo) { // TODO
//        this.persistTo = persistTo;
//    }
//
//    @Override
//    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
//        return new PersistorConverter<>(retrofit.nextResponseBodyConverter(this, type, annotations));
//    }
//
//    private class PersistorConverter<T> implements Converter<ResponseBody, T> {
//
//        private final Converter<ResponseBody, T> delegate;
//
//        PersistorConverter(Converter<ResponseBody, T> delegate) {
//            this.delegate = delegate;
//        }
//
//        @Override
//        public T convert(ResponseBody value) throws IOException {
//            T response = delegate.convert(value);
//
//            if (response instanceof GenerateImagesResponse) {
//                ((GenerateImagesResponse) response).data()
//                    .forEach(data -> {
//                        try {
//                            data.url(
//                                data.url() != null
//                                    ? FilePersistor.persistFromUri(data.url(), persistTo).toUri()
//                                    : FilePersistor.persistFromBase64String(data.b64Json(), persistTo).toUri()
//                            );
//                        } catch (IOException e) {
//                            throw new RuntimeException(e);
//                        }
//                    });
//            }
//
//            return response;
//        }
//    }
//}
