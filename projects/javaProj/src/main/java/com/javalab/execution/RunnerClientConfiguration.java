package com.javalab.execution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
@Configuration
public class RunnerClientConfiguration{
 @Bean @Qualifier("javaRunnerRestClient") RestClient javaRunnerRestClient(RestClient.Builder builder,Environment env){
  HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  JdkClientHttpRequestFactory factory=new JdkClientHttpRequestFactory(client);factory.setReadTimeout(Duration.ofSeconds(12));
  return builder.baseUrl(env.getProperty("javalab.runner.url","http://localhost:8091")).requestFactory(factory).build();
 }
}
