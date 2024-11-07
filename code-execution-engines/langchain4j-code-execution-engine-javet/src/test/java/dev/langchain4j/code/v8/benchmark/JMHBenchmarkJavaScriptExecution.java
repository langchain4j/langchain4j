package dev.langchain4j.code.v8.benchmark;


import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.code.graalvm.GraalVmJavaScriptExecutionEngine;
import dev.langchain4j.code.v8.V8JavaScriptExecutionEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.CommandLineOptionException;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 5)
@Measurement(iterations = 2)
public class JMHBenchmarkJavaScriptExecution {


    private String code;
    private CodeExecutionEngine v8Engine;
    private CodeExecutionEngine graalEngine;


    @Setup(Level.Trial)
    public void setUp(){
        code = """
            function fibonacci(n) {
                    if (n <= 1) return n;
                    return fibonacci(n - 1) + fibonacci(n - 2);
                }
                                
            fibonacci(15)
            """;

        graalEngine = new GraalVmJavaScriptExecutionEngine();
        v8Engine = V8JavaScriptExecutionEngine.getInstance();
    }


    @Benchmark
    public void benchmarkGraal(){
        graalEngine.execute(code);
    }


    @Benchmark
    public void benchmarkV8(){
        v8Engine.execute(code);
    }


    public static void main(String[] args) throws RunnerException, CommandLineOptionException {
        Options opt = new OptionsBuilder()
                .parent(new CommandLineOptions(args))
                .timeUnit(TimeUnit.MILLISECONDS)
                .include(JMHBenchmarkJavaScriptExecution.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }


}
