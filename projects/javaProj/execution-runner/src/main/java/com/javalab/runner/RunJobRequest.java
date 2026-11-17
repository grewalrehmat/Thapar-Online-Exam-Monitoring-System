package com.javalab.runner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RunJobRequest(@NotBlank @Size(max=100000) String sourceCode,@Size(max=10000) String standardInput){}
