package com.javalab;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class JavaLabApplication {
    public static void main(String[] args) { SpringApplication.run(JavaLabApplication.class, args); }
}
