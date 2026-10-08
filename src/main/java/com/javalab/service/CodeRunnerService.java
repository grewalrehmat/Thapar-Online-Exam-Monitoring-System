package com.javalab.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class CodeRunnerService {

    public record RunResult(String stdout, String stderr, int exitCode, long executionTimeMs) {}

    public RunResult run(String source) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("javalab-run-");
            Path sourceFile = dir.resolve("Main.java");
            Files.writeString(sourceFile, source == null ? "" : source);

            Process compile = new ProcessBuilder(
                    "javac", "-encoding", "UTF-8", sourceFile.toString())
                    .directory(dir.toFile())
                    .redirectErrorStream(false)
                    .start();

            String compileErr = new String(compile.getErrorStream().readAllBytes());
            boolean compiled = compile.waitFor(8, TimeUnit.SECONDS);

            if (!compiled) {
                compile.destroyForcibly();
                return new RunResult("", "Compilation timed out.", 124, 8000);
            }

            if (compile.exitValue() != 0) {
                return new RunResult("", compileErr, compile.exitValue(), 0);
            }

            long start = System.currentTimeMillis();

            Process run = new ProcessBuilder(
                    "java", "-Xmx128m", "-Djava.security.manager=disallow", "-cp",
                    dir.toString(), "Main")
                    .directory(dir.toFile())
                    .redirectErrorStream(false)
                    .start();

            boolean finished = run.waitFor(3, TimeUnit.SECONDS);

            if (!finished) {
                run.destroyForcibly();
                return new RunResult(
                        new String(run.getInputStream().readAllBytes()),
                        "Execution timed out (3 second limit).",
                        124,
                        System.currentTimeMillis() - start);
            }

            String stdout = new String(run.getInputStream().readAllBytes());
            String stderr = new String(run.getErrorStream().readAllBytes());

            return new RunResult(
                    stdout,
                    stderr,
                    run.exitValue(),
                    System.currentTimeMillis() - start
            );

        } catch (Exception e) {
            return new RunResult("", e.getMessage(), 1, 0);
        } finally {
            if (dir != null) {
                try {
                    Files.walk(dir)
                            .sorted((a, b) -> b.compareTo(a))
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                } catch (IOException ignored) {}
            }
        }
    }
}
