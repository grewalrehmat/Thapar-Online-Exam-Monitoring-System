package com.javalab.runner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
@Service public class DockerJavaExecutor {
 private static final int OUTPUT_LIMIT=20000;
 private final String docker,image;private final long timeoutSeconds;
 public DockerJavaExecutor(@Value("${javalab.runner.docker-binary:docker}") String docker,@Value("${javalab.runner.image:javalab-java-runner:21}") String image,@Value("${javalab.runner.timeout-seconds:5}") long timeoutSeconds){this.docker=docker;this.image=image;this.timeoutSeconds=timeoutSeconds;}
 public RunJobResult run(RunJobRequest request){
  long started=System.nanoTime();Path dir=null;String name="javalab-run-"+UUID.randomUUID().toString().replace("-","");Process process=null;
  try{
   dir=Files.createTempDirectory(Path.of("/tmp"),"javalab-").toRealPath();Files.writeString(dir.resolve("Main.java"),request.sourceCode(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
   String uid=commandOutput("id","-u"),gid=commandOutput("id","-g");if(uid.equals("0"))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Runner must run as a non-root host user.");
   List<String> args=List.of(docker,"run","--pull=never","--rm","--interactive","--name",name,"--network","none","--memory=256m","--memory-swap=256m","--cpus=0.5","--pids-limit=64","--read-only","--cap-drop=ALL","--security-opt=no-new-privileges","--user",uid+":"+gid,"--tmpfs","/tmp:rw,nosuid,nodev,noexec,size=16m","--tmpfs","/workspace:rw,nosuid,nodev,size=32m,mode=1777","--mount","type=bind,src="+dir+",dst=/input,readonly","--workdir","/workspace","--entrypoint","/bin/sh",image,"-c","cp /input/Main.java Main.java && javac -encoding UTF-8 Main.java && exec java -Xms16m -Xmx96m -XX:ActiveProcessorCount=1 -cp /workspace Main");
   process=new ProcessBuilder(args).start();Process running=process;
   CompletableFuture<String> out=CompletableFuture.supplyAsync(()->readBounded(running.getInputStream()));
   CompletableFuture<String> err=CompletableFuture.supplyAsync(()->readBounded(running.getErrorStream()));
   try(OutputStream in=process.getOutputStream()){in.write((request.standardInput()==null?"":request.standardInput()).getBytes(StandardCharsets.UTF_8));}
   boolean finished=process.waitFor(timeoutSeconds,TimeUnit.SECONDS);
   if(!finished){process.destroyForcibly();process.waitFor(2,TimeUnit.SECONDS);stop(name);}
   String stdout=out.get(3,TimeUnit.SECONDS),stderr=err.get(3,TimeUnit.SECONDS);long ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);int exit=finished?process.exitValue():124;
   if(finished&&(exit==125||stderr.contains("Cannot connect to the Docker daemon")||stderr.contains("No such image")||stderr.contains("pull access denied")))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Java runner is not ready. Check Docker and the prebuilt runner image.");
   return new RunJobResult(stdout,stderr,exit,!finished,ms);
  }catch(ResponseStatusException e){throw e;}catch(IOException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Java runner could not start. Check Docker availability.",e);}
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Java run was interrupted.",e);}
   catch(ExecutionException|TimeoutException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Could not collect Java run output.",e);}
  finally{if(process!=null&&process.isAlive())process.destroyForcibly();if(dir!=null)deleteTree(dir);}
 }
 private String commandOutput(String... args)throws IOException,InterruptedException{Process p=new ProcessBuilder(args).start();String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();if(!p.waitFor(2,TimeUnit.SECONDS)||p.exitValue()!=0)throw new IOException("Could not determine runner uid/gid.");return text;}
 private String readBounded(InputStream stream){
  try(stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] b=new byte[4096];int n;boolean cut=false;
   while((n=stream.read(b))!=-1){int left=OUTPUT_LIMIT-out.size();if(left>0)out.write(b,0,Math.min(left,n));if(n>left)cut=true;}
   return out.toString(StandardCharsets.UTF_8)+(cut?System.lineSeparator()+"[output truncated]":"");
  }catch(IOException e){return "[could not read output]";}
 }
 private void stop(String name){try{Process p=new ProcessBuilder(docker,"rm","-f",name).start();if(!p.waitFor(2,TimeUnit.SECONDS))p.destroyForcibly();}catch(Exception ignored){}}
 private void deleteTree(Path dir){try(var files=Files.walk(dir)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
