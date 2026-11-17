package com.javalab.runner;
public record RunJobResult(String standardOutput,String standardError,Integer exitCode,boolean timedOut,long durationMs){}
