package com.javalab.submissions;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record CodeRequest(@NotNull @Size(max=100000) String sourceCode,@Size(max=10000) String standardInput){}
