package com.javalab.web;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler{
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> badRequest(IllegalArgumentException ex){return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message",ex.getMessage()));}
 @ExceptionHandler(IllegalStateException.class) ResponseEntity<?> conflict(IllegalStateException ex){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code","CONFLICT","message",ex.getMessage()));}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("code","REQUEST_FAILED","message",ex.getReason()==null?"Request failed.":ex.getReason()));}
}
