package com.speakflow.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequestMapping("/api/v1")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository repository;
    private final JdbcTemplate jdbc;
    public AuthController(AuthenticationManager am, SecurityContextRepository repository, JdbcTemplate jdbc) {
        this.authenticationManager=am; this.repository=repository; this.jdbc=jdbc;
    }
    public record LoginRequest(@NotBlank String username,@NotBlank String password) {}

    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
    }
    @PostMapping("/auth/login") public Map<String,Object> login(@Valid @RequestBody LoginRequest login,
               HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication=authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(login.username(),login.password()));
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        repository.saveContext(context,request,response);
        return me(authentication);
    }
    @GetMapping("/auth/me") public Map<String,Object> me(Authentication authentication) {
        if(authentication==null || !authentication.isAuthenticated())
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return jdbc.queryForObject("SELECT id,username,display_name,timezone FROM sf_user WHERE username=?",
            (rs,n)->Map.<String,Object>of("id",rs.getLong("id"),"username",rs.getString("username"),
                "displayName",rs.getString("display_name"),"timezone",rs.getString("timezone")),authentication.getName());
    }
    @GetMapping("/health") public Map<String,Object> health(){ return Map.of("status","ok"); }
}
