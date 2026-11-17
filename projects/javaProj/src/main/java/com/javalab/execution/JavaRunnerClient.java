package com.javalab.execution;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
@Component
public class JavaRunnerClient{
 private final RestClient client;private final String token;
 public JavaRunnerClient(@Qualifier("javaRunnerRestClient") RestClient javaRunnerRestClient,Environment env){this.client=javaRunnerRestClient;this.token=env.getProperty("javalab.runner.token","local-dev-runner-token");}
 public RunnerResponse run(String source,String stdin){
  try{RunnerResponse result=client.post().uri("/internal/run").contentType(MediaType.APPLICATION_JSON).header("X-Runner-Token",token).body(new RunnerRequest(source,stdin)).retrieve().body(RunnerResponse.class);
   if(result==null)throw new IllegalStateException("The Java runner returned an empty response.");return result;
  }catch(RestClientResponseException ex){
   String message=ex.getStatusCode().value()==401
    ? "Java runner authentication failed. Check that JAVALAB_RUNNER_TOKEN matches in both services."
    : ex.getResponseBodyAsString().contains("runner is not ready")
      ? "Java runner is connected, but Docker or the javalab-java-runner:21 image is not ready."
      : "Java runner returned an error (HTTP "+ex.getStatusCode().value()+"). Check the runner terminal.";
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message,ex);
  }catch(RestClientException ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cannot reach Java runner at "+client.toString()+". Make sure it is running on port 8091.",ex);}
 }
}
