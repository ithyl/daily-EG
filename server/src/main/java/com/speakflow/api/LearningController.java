package com.speakflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.speakflow.service.FeedbackService;
import com.speakflow.service.LearningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController @RequestMapping("/api/v1")
public class LearningController {
    private final LearningService learning;
    private final JdbcTemplate jdbc;
    private final FeedbackService feedback;
    private final ObjectMapper mapper;
    public LearningController(LearningService learning,JdbcTemplate jdbc, FeedbackService feedback,ObjectMapper mapper){
        this.learning=learning;this.jdbc=jdbc;this.feedback=feedback;this.mapper=mapper;
    }
    private long user(Authentication auth){return learning.userId(auth.getName());}
    public record StepBody(boolean completed){}
    public record AttemptBody(@NotBlank @Size(min=2,max=6000) String text){}
    public record ReviewBody(boolean remembered){}

    @GetMapping("/lessons") public List<Map<String,Object>> lessons(Authentication auth){ return learning.list(user(auth)); }
    @GetMapping("/lessons/latest") public Map<String,Object> latest(Authentication auth){
        var all=learning.list(user(auth));
        if(all.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No lessons published");
        return all.get(0);
    }
    @GetMapping("/lessons/{id}") public Map<String,Object> lesson(@PathVariable long id,Authentication auth){
        return learning.detail(user(auth),id);
    }
    @PutMapping("/lessons/{id}/steps/{step}") public Map<String,Object> step(@PathVariable long id,
        @PathVariable String step,@RequestBody StepBody body,Authentication auth){
        return learning.markStep(user(auth),id,step,body.completed());
    }
    @GetMapping("/statistics") public Map<String,Object> statistics(Authentication auth){return learning.stats(user(auth));}
    @GetMapping("/reviews/due") public List<Map<String,Object>> due(Authentication auth){return learning.dueCards(user(auth));}
    @PostMapping("/words/{id}/save") public Map<String,Object> saveWord(@PathVariable long id,Authentication auth){
        learning.saveWord(user(auth),id);return Map.of("saved",true);
    }
    @PostMapping("/reviews/{id}/grade") public Map<String,Object> grade(@PathVariable long id,
        @RequestBody ReviewBody body,Authentication auth){
        learning.gradeWord(user(auth),id,body.remembered());return Map.of("saved",true);
    }
    @PostMapping("/exercises/{id}/attempts") public Map<String,Object> attempt(@PathVariable long id,
        @Valid @RequestBody AttemptBody body,Authentication auth) throws Exception {
        long userId=user(auth);
        Map<String,String> exercise=jdbc.query("""
            SELECT e.question_en,e.reference_answer FROM sf_exercise e
            JOIN sf_lesson l ON l.id=e.lesson_id
            JOIN sf_plan_member m ON m.plan_id=l.plan_id
            WHERE e.id=? AND m.user_id=?
            """,rs->rs.next()?Map.of("question",rs.getString(1),"reference",rs.getString(2)):null,id,userId);
        if(exercise==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Integer recent=jdbc.queryForObject("SELECT COUNT(*) FROM sf_speaking_attempt WHERE user_id=? AND created_at>DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 15 MINUTE)",Integer.class,userId);
        if(recent!=null&&recent>=30)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"练习提交过于频繁，请15分钟后再试");
        Map<String,Object> result=feedback.evaluate(exercise.get("question"),body.text(),exercise.get("reference"));
        String json=mapper.writeValueAsString(result);
        Integer attemptNo=jdbc.queryForObject(
            "SELECT COALESCE(MAX(attempt_no),0)+1 FROM sf_speaking_attempt WHERE user_id=? AND exercise_id=?",
            Integer.class,userId,id);
        jdbc.update("INSERT INTO sf_speaking_attempt(user_id,exercise_id,transcript,feedback_json,feedback_mode,input_type,attempt_no) VALUES(?,?,?,?,?,'TEXT',?)",
            userId,id,body.text(),json,result.get("mode"),attemptNo);
        return result;
    }
}
