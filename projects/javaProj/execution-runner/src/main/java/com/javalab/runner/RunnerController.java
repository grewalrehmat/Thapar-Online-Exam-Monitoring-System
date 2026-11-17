package com.javalab.runner;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@RestController public class RunnerController {
 private final DockerJavaExecutor executor; private final byte[] expectedToken;
 public RunnerController(DockerJavaExecutor executor,@Value("${javalab.runner.token:}") String token){this.executor=executor;this.expectedToken=token.getBytes(StandardCharsets.UTF_8);}
 @GetMapping("/health") public ResponseEntity<String> health(){return ResponseEntity.ok("ok");}
 @PostMapping("/internal/run") public RunJobResult run(@RequestHeader(value="X-Runner-Token",required=false) String token,@Valid @RequestBody RunJobRequest request){
  if(expectedToken.length<24||token==null||!MessageDigest.isEqual(expectedToken,token.getBytes(StandardCharsets.UTF_8)))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid runner credentials.");
  return executor.run(request);
 }
}
