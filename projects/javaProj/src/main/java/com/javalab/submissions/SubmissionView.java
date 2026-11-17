package com.javalab.submissions;
import java.time.Instant;
public record SubmissionView(Long id,String sourceCode,SubmissionStatus status,Instant lastSavedAt,Instant submittedAt){
 public static SubmissionView from(Submission s){return new SubmissionView(s.getId(),s.getSourceCode(),s.getStatus(),s.getLastSavedAt(),s.getSubmittedAt());}
}
