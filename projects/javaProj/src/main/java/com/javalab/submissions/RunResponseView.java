package com.javalab.submissions;
import java.time.Instant;
public record RunResponseView(String standardOutput,String standardError,Integer exitCode,boolean timedOut,long durationMs,Instant completedAt){}
