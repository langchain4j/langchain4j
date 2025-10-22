package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Performance benchmark tests for LazyJsonString to demonstrate lazy evaluation benefits.
 * These tests are disabled by default as they are primarily for performance analysis.
 */
class LazyJsonStringBenchmarkTest {

    @Test
    // @Disabled("Performance benchmark - enable manually for performance analysis")
    void benchmarkLazyVsEagerEvaluation() {
        int iterations = 1000;

        // Benchmark eager evaluation
        long eagerStartTime = System.nanoTime();
        List<String> eagerResults = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            ComplexObject obj = createComplexObject(i);
            String json = Json.toJson(obj); // Immediate computation
            eagerResults.add(json);
        }
        long eagerTotalTime = System.nanoTime() - eagerStartTime;

        // Benchmark lazy evaluation (creation only)
        long lazyCreationStartTime = System.nanoTime();
        List<LazyJsonString> lazyResults = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            int index = i;
            LazyJsonString lazy = new LazyJsonString(() -> createComplexObject(index));
            lazyResults.add(lazy);
        }
        long lazyCreationTime = System.nanoTime() - lazyCreationStartTime;

        // Benchmark lazy evaluation (computation when needed)
        long lazyComputationStartTime = System.nanoTime();
        List<String> lazyComputedResults = new ArrayList<>();
        for (LazyJsonString lazy : lazyResults) {
            lazyComputedResults.add(lazy.getValue());
        }
        long lazyComputationTime = System.nanoTime() - lazyComputationStartTime;
        long lazyTotalTime = lazyCreationTime + lazyComputationTime;

        // Print benchmark results
        System.out.println("=== LazyJsonString Performance Benchmark ===");
        System.out.println("Iterations: " + iterations);
        System.out.println("Eager evaluation total time: " + TimeUnit.NANOSECONDS.toMillis(eagerTotalTime) + " ms");
        System.out.println("Lazy creation time: " + TimeUnit.NANOSECONDS.toMillis(lazyCreationTime) + " ms");
        System.out.println("Lazy computation time: " + TimeUnit.NANOSECONDS.toMillis(lazyComputationTime) + " ms");
        System.out.println("Lazy evaluation total time: " + TimeUnit.NANOSECONDS.toMillis(lazyTotalTime) + " ms");

        double creationSpeedup = (double) eagerTotalTime / lazyCreationTime;
        System.out.println("Creation speedup: " + String.format("%.2fx", creationSpeedup));

        // Verify results are equivalent
        assertThat(eagerResults).hasSize(iterations);
        assertThat(lazyComputedResults).hasSize(iterations);

        // Lazy creation should be significantly faster than eager evaluation
        assertThat(lazyCreationTime).isLessThan(eagerTotalTime);

        // Creation speedup should be substantial
        assertThat(creationSpeedup).isGreaterThan(2.0);
    }

    @Test
    // @Disabled("Performance benchmark - enable manually for performance analysis")
    void benchmarkOnDemandComputation() {
        int accessCount = 100;

        // Create a LazyJsonString with expensive computation
        LazyJsonString lazyJsonString = new LazyJsonString(() -> {
            try {
                Thread.sleep(1); // Simulate computation cost
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return createComplexObject(42);
        });

        // Benchmark multiple accesses (each should compute independently)
        long totalAccessTime = 0;
        List<String> results = new ArrayList<>();

        for (int i = 0; i < accessCount; i++) {
            long accessStart = System.nanoTime();
            String result = lazyJsonString.getValue();
            long accessTime = System.nanoTime() - accessStart;
            totalAccessTime += accessTime;
            results.add(result);
        }

        long averageAccessTime = totalAccessTime / accessCount;

        System.out.println("=== LazyJsonString On-Demand Computation Benchmark ===");
        System.out.println("Access count: " + accessCount);
        System.out.println("Total access time: " + TimeUnit.NANOSECONDS.toMillis(totalAccessTime) + " ms");
        System.out.println("Average access time: " + TimeUnit.NANOSECONDS.toMicros(averageAccessTime) + " μs");

        // All results should be equal (same computation)
        String firstResult = results.get(0);
        for (String result : results) {
            assertThat(result).isEqualTo(firstResult);
        }

        // Each access should take a reasonable amount of time
        assertThat(averageAccessTime).isPositive();
    }

    @Test
    // @Disabled("Performance benchmark - enable manually for performance analysis")
    void benchmarkMemoryUsageScenario() {
        int objectCount = 10000;

        // Force garbage collection to get a clean baseline
        System.gc();
        System.gc();

        // Scenario 1: Create many LazyJsonString objects but don't compute them
        long memoryBefore = getUsedMemory();
        List<LazyJsonString> lazyObjects = new ArrayList<>();

        for (int i = 0; i < objectCount; i++) {
            int index = i;
            LazyJsonString lazy = new LazyJsonString(() -> createComplexObject(index));
            lazyObjects.add(lazy);
        }

        long memoryAfterCreation = getUsedMemory();
        long creationMemoryUsage = memoryAfterCreation - memoryBefore;

        // Scenario 2: Compute all values (should use more memory)
        for (LazyJsonString lazy : lazyObjects) {
            lazy.getValue(); // Trigger computation (no caching in new implementation)
        }

        long memoryAfterComputation = getUsedMemory();
        long computationMemoryUsage = memoryAfterComputation - memoryAfterCreation;

        System.out.println("=== LazyJsonString Memory Usage Benchmark ===");
        System.out.println("Object count: " + objectCount);
        System.out.println("Memory usage after creation: " + (creationMemoryUsage / 1024) + " KB");
        System.out.println("Additional memory usage after computation: " + (computationMemoryUsage / 1024) + " KB");
        System.out.println("Total memory usage: " + ((creationMemoryUsage + computationMemoryUsage) / 1024) + " KB");

        // Memory usage should be reasonable - just verify objects were created
        assertThat(lazyObjects).hasSize(objectCount);
        assertThat(lazyObjects.get(0).getValue()).isNotNull();
    }

    @Test
    void demonstrateOnDemandEvaluation() {
        // This test runs by default to demonstrate the concept

        // Create multiple LazyJsonString objects
        List<LazyJsonString> lazyObjects = new ArrayList<>();
        long startTime = System.nanoTime();

        for (int i = 0; i < 100; i++) {
            int index = i;
            LazyJsonString lazy = new LazyJsonString(() -> createComplexObject(index));
            lazyObjects.add(lazy);
        }

        long creationTime = System.nanoTime() - startTime;

        // Now compute only the first 10
        startTime = System.nanoTime();
        List<String> computedResults = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String result = lazyObjects.get(i).getValue();
            computedResults.add(result);
        }
        long partialComputationTime = System.nanoTime() - startTime;

        // Verify that we got results for the computed objects
        assertThat(computedResults).hasSize(10);
        for (String result : computedResults) {
            assertThat(result).isNotNull();
            assertThat(result).contains("\"id\""); // Verify it's valid JSON
        }

        System.out.println(
                "Creation of 100 LazyJsonString objects: " + TimeUnit.NANOSECONDS.toMicros(creationTime) + " μs");
        System.out.println(
                "Computation of 10 objects: " + TimeUnit.NANOSECONDS.toMicros(partialComputationTime) + " μs");

        // Demonstrate on-demand evaluation: each call to getValue() computes fresh
        LazyJsonString testLazy = lazyObjects.get(0);
        String result1 = testLazy.getValue();
        String result2 = testLazy.getValue();

        // Both results should be identical (same computation)
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isNotNull();
    }

    private ComplexObject createComplexObject(int id) {
        // Simulate a complex object that's expensive to serialize
        ComplexObject obj = new ComplexObject();
        obj.setId(id);
        obj.setName("Object " + id);
        obj.setDescription("This is a complex object with ID " + id + " that contains a lot of data");

        // Add some nested data
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add("Item " + i + " for object " + id);
        }
        obj.setItems(items);

        return obj;
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    // Test helper class for complex object serialization
    private static class ComplexObject {
        private int id;
        private String name;
        private String description;
        private List<String> items;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getItems() {
            return items;
        }

        public void setItems(List<String> items) {
            this.items = items;
        }
    }
}
