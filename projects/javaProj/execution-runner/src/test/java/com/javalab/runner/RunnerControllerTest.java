package com.javalab.runner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RunnerController.class)
@TestPropertySource(properties="javalab.runner.token=test-runner-token-value-over-24-chars")
class RunnerControllerTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper mapper;
 @MockBean DockerJavaExecutor executor;

 @Test void healthIsAvailable() throws Exception {mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(content().string("ok"));}
 @Test void rejectsMissingOrIncorrectToken() throws Exception {
  String body=mapper.writeValueAsString(new RunJobRequest("public class Main{}",""));
  mvc.perform(post("/internal/run").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
 }
 @Test void returnsRunnerResultForAuthenticatedRequest() throws Exception {
  when(executor.run(any())).thenReturn(new RunJobResult("42","",0,false,110));
  String body=mapper.writeValueAsString(new RunJobRequest("public class Main{}",""));
  mvc.perform(post("/internal/run").header("X-Runner-Token","test-runner-token-value-over-24-chars").contentType("application/json").content(body))
   .andExpect(status().isOk()).andExpect(jsonPath("$.standardOutput").value("42")).andExpect(jsonPath("$.exitCode").value(0));
 }
}
