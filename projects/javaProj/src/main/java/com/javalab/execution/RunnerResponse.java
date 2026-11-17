package com.javalab.execution;
public record RunnerResponse(String standardOutput,String standardError,Integer exitCode,boolean timedOut,long durationMs){}
