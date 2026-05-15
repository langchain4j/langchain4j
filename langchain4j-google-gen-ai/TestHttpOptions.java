import java.lang.reflect.Method;
public class TestHttpOptions {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = Class.forName("com.google.genai.types.HttpOptions$Builder");
        for (Method m : clazz.getMethods()) {
            System.out.println(m.getName() + " " + java.util.Arrays.toString(m.getParameterTypes()));
        }
    }
}
