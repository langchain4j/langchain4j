package dev.langchain4j.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Nested;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LazyJsonStringTest {

    @Nested
    class ConstructorTests {
        
        @Test
        void shouldCreateLazyJsonStringWithValidSupplier() {
            Supplier<Object> supplier = () -> "test";
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            assertThat(lazyJsonString).isNotNull();
        }
        
        @Test
        void shouldThrowExceptionWhenSupplierIsNull() {
            assertThatThrownBy(() -> new LazyJsonString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value supplier cannot be null");
        }
    }
    
    @Nested
    class OnDemandEvaluationTests {
        
        @Test
        void shouldComputeValueOnEachGetValueCall() {
            AtomicInteger callCount = new AtomicInteger(0);
            Supplier<Object> supplier = () -> {
                callCount.incrementAndGet();
                return "test";
            };
            
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            // First call
            String result1 = lazyJsonString.getValue();
            assertThat(callCount.get()).isEqualTo(1);
            assertThat(result1).isEqualTo("test");
            
            // Second call - should compute again (no caching)
            String result2 = lazyJsonString.getValue();
            assertThat(callCount.get()).isEqualTo(2);
            assertThat(result2).isEqualTo("test");
            
            // Third call - should compute again
            String result3 = lazyJsonString.getValue();
            assertThat(callCount.get()).isEqualTo(3);
            assertThat(result3).isEqualTo("test");
        }
    }
    
    @Nested
    class SpecialTypeHandlingTests {
        
        @Test
        void shouldReturnSuccessForNullValue() {
            Supplier<Object> supplier = () -> null;
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            String result = lazyJsonString.getValue();
            
            assertThat(result).isEqualTo("Success");
        }
        
        @Test
        void shouldReturnStringDirectlyWithoutJsonProcessing() {
            String testString = "Hello, World!";
            Supplier<Object> supplier = () -> testString;
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            String result = lazyJsonString.getValue();
            
            assertThat(result).isEqualTo(testString);
        }
        
        @Test
        void shouldSerializeComplexObjectsToJson() {
            TestObject testObject = new TestObject("John", 30);
            Supplier<Object> supplier = () -> testObject;
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            String result = lazyJsonString.getValue();
            
            // Check that the result contains the expected JSON elements
            assertThat(result).contains("John");
            assertThat(result).contains("30");
        }
        
        @Test
        void shouldHandleListsAndArrays() {
            List<String> testList = List.of("apple", "banana", "cherry");
            Supplier<Object> supplier = () -> testList;
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            String result = lazyJsonString.getValue();
            
            assertThat(result).contains("apple", "banana", "cherry");
        }
    }
    
    @Nested
    class ErrorHandlingTests {
        
        @Test
        void shouldFallbackToToStringOnJsonSerializationError() {
            Object problematicObject = new Object() {
                @Override
                public String toString() {
                    return "fallback-string";
                }
            };
            Supplier<Object> supplier = () -> problematicObject;
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            String result = lazyJsonString.getValue();
            
            assertThat(result).isEqualTo("fallback-string");
        }
        
        @Test
        void shouldHandleSupplierExceptionGracefully() {
            Supplier<Object> supplier = () -> {
                throw new RuntimeException("Supplier failed");
            };
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            String result = lazyJsonString.getValue();
            
            assertThat(result).startsWith("Error:");
        }
        
        @Test
        void shouldHandleBothSupplierAndFallbackExceptions() {
            Supplier<Object> supplier = () -> {
                throw new RuntimeException("Both supplier and fallback failed");
            };
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            String result = lazyJsonString.getValue();
            
            assertThat(result).startsWith("Error:");
        }
    }
    
    @Nested
    class ThreadSafetyTests {
        
        @RepeatedTest(10)
        void shouldHandleConcurrentAccesses() throws InterruptedException {
            AtomicInteger callCount = new AtomicInteger(0);
            Supplier<Object> supplier = () -> {
                callCount.incrementAndGet();
                return "test-" + callCount.get();
            };
            
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<String> results = Collections.synchronizedList(new ArrayList<>());
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        String result = lazyJsonString.getValue();
                        results.add(result);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertThat(completed).isTrue();
            assertThat(results).hasSize(threadCount);
            // Each call should compute independently, so we should have different results
            assertThat(callCount.get()).isEqualTo(threadCount);
        }
        
        @Test
        void shouldHandleConcurrentAccessWithExceptions() throws InterruptedException {
            Supplier<Object> supplier = () -> {
                throw new RuntimeException("Test exception");
            };
            
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<String> results = Collections.synchronizedList(new ArrayList<>());
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        String result = lazyJsonString.getValue();
                        results.add(result);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertThat(completed).isTrue();
            assertThat(results).hasSize(threadCount);
            // All results should be error messages
            assertThat(results).allMatch(result -> result.startsWith("Error:"));
        }
    }
    
    @Nested
    class ToStringTests {
        
        @Test
        void shouldReturnPlaceholderForOnDemandEvaluation() {
            Supplier<Object> supplier = () -> "test";
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            
            String result = lazyJsonString.toString();
            
            assertThat(result).isEqualTo("[LazyJsonString: computed on demand]");
        }
    }
    
    @Nested
    class EqualsAndHashCodeTests {
        
        @Test
        void shouldBeEqualWhenComputedValuesAreEqual() {
            LazyJsonString lazy1 = new LazyJsonString(() -> "test");
            LazyJsonString lazy2 = new LazyJsonString(() -> "test");
            
            assertThat(lazy1).isEqualTo(lazy2);
        }
        
        @Test
        void shouldNotBeEqualWhenComputedValuesAreDifferent() {
            LazyJsonString lazy1 = new LazyJsonString(() -> "test1");
            LazyJsonString lazy2 = new LazyJsonString(() -> "test2");
            
            assertThat(lazy1).isNotEqualTo(lazy2);
        }
        
        @Test
        void shouldBeEqualToItself() {
            LazyJsonString lazy = new LazyJsonString(() -> "test");
            
            assertThat(lazy).isEqualTo(lazy);
        }
        
        @Test
        void shouldNotBeEqualToNull() {
            LazyJsonString lazy = new LazyJsonString(() -> "test");
            
            assertThat(lazy).isNotEqualTo(null);
        }
        
        @Test
        void shouldNotBeEqualToDifferentClass() {
            LazyJsonString lazy = new LazyJsonString(() -> "test");
            
            assertThat(lazy).isNotEqualTo("test");
        }
        
        @Test
        void shouldHandleNullValuesInEquals() {
            LazyJsonString lazy1 = new LazyJsonString(() -> null);
            LazyJsonString lazy2 = new LazyJsonString(() -> null);
            
            assertThat(lazy1).isEqualTo(lazy2);
        }
    }
    
    @Nested
    class PerformanceTests {
        
        @Test
        void shouldDemonstrateOnDemandEvaluation() {
            AtomicInteger callCount = new AtomicInteger(0);
            
            // Create multiple LazyJsonString objects
            List<LazyJsonString> lazyStrings = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int index = i;
                lazyStrings.add(new LazyJsonString(() -> {
                    callCount.incrementAndGet();
                    return new TestObject("User" + index, 20 + index);
                }));
            }
            
            // At this point, no computation should have happened
            assertThat(callCount.get()).isEqualTo(0);
            
            // Compute only a few of them
            for (int i = 0; i < 10; i++) {
                String result = lazyStrings.get(i).getValue();
                assertThat(result).isNotNull();
            }
            
            // Only 10 computations should have happened
            assertThat(callCount.get()).isEqualTo(10);
            
            // Accessing the same objects again should trigger new computations
            for (int i = 0; i < 5; i++) {
                String result = lazyStrings.get(i).getValue();
                assertThat(result).isNotNull();
            }
            
            // 5 more computations should have happened (no caching)
            assertThat(callCount.get()).isEqualTo(15);
        }
    }
    
    // Test helper class
    private static class TestObject {
        private final String name;
        private final int age;
        
        public TestObject(String name, int age) {
            this.name = name;
            this.age = age;
        }
        
        public String getName() {
            return name;
        }
        
        public int getAge() {
            return age;
        }
    }
}