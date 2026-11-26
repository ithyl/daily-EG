package com.speakflow.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<Map<String,String>> unreadable(Exception ex){return ResponseEntity.badRequest().body(Map.of("message","请求格式或日期无效"));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Map<String,String>> conflict(Exception ex){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","课程日期冲突或内容超出数据库字段限制。本批次已回滚，请刷新后检查数据再重试。"));}
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String,String>> badCredentials(Exception ex){
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message","用户名或密码错误"));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> invalid(Exception ex){
        return ResponseEntity.badRequest().body(Map.of("message","输入内容无效，请检查后重试"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> status(ResponseStatusException ex){
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",ex.getReason()==null?"请求失败":ex.getReason()));
    }
}
