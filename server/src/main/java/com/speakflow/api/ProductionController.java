package com.speakflow.api;

import com.speakflow.ai.AIConfig;
import com.speakflow.ai.OpenAIClient;
import com.speakflow.service.AudioService;
import com.speakflow.service.FeedbackService;
import com.speakflow.service.LearningService;
import com.speakflow.service.LessonGenerationService;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class ProductionController {
    private final LearningService learning;
    private final LessonGenerationService generation;
    private final AudioService audio;
    private final AIConfig config;
    private final OpenAIClient client;
    private final FeedbackService feedback;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ProductionController(LearningService learning,LessonGenerationService generation,AudioService audio,
        AIConfig config,OpenAIClient client,FeedbackService feedback,JdbcTemplate jdbc,ObjectMapper mapper){
        this.learning=learning;this.generation=generation;this.audio=audio;this.config=config;
        this.client=client;this.feedback=feedback;this.jdbc=jdbc;this.mapper=mapper;
    }
    private long user(Authentication auth){return learning.userId(auth.getName());}

    @GetMapping("/features")public Map<String,Object> features(Authentication auth){
        return Map.of("aiEnabled",config.enabled(),"ttsEnabled",config.enabled(),"asrEnabled",config.enabled(),
            "audioPrivacy","原始录音仅发送至配置的 ASR 服务进行转写，服务器不持久保存录音文件");
    }
    @GetMapping("/plans")public List<Map<String,Object>> plans(Authentication auth){return generation.plans(user(auth));}
    @GetMapping("/admin/generation-jobs")public List<Map<String,Object>> jobs(Authentication auth){return generation.jobs(user(auth));}
    public record GenerateBody(long planId,@DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){}
    @PostMapping("/admin/generate-lesson")public Map<String,Object> generate(@RequestBody GenerateBody body,Authentication auth) throws Exception {
        if(body.planId()<=0||body.date()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"缺少计划ID或日期");
        return generation.generateOwned(user(auth),body.planId(),body.date());
    }
    @GetMapping(value="/lessons/{lessonId}/audio",produces="audio/mpeg")
    public ResponseEntity<byte[]> lessonAudio(@PathVariable long lessonId,
        @RequestParam(required=false) Long segmentId,Authentication auth) throws Exception {
        byte[] bytes=audio.get(user(auth),lessonId,segmentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType("audio/mpeg")).body(bytes);
    }
    @PostMapping(value="/exercises/{exerciseId}/audio-attempts",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> audioAttempt(@PathVariable long exerciseId,
        @RequestPart("file") MultipartFile file,Authentication auth) throws Exception {
        long userId=user(auth);
        Map<String,String> exercise=jdbc.query("""
            SELECT e.question_en,e.reference_answer FROM sf_exercise e
            JOIN sf_lesson l ON l.id=e.lesson_id JOIN sf_plan_member m ON m.plan_id=l.plan_id
            WHERE e.id=? AND m.user_id=? AND l.status='PUBLISHED'
            """,rs->rs.next()?Map.of("question",rs.getString(1),"reference",rs.getString(2)):null,exerciseId,userId);
        if(exercise==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"题目不存在");
        Integer recent=jdbc.queryForObject("SELECT COUNT(*) FROM sf_speaking_attempt WHERE user_id=? AND created_at>DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 15 MINUTE)",Integer.class,userId);
        if(recent!=null&&recent>=30)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"练习提交过于频繁，请15分钟后再试");
        String mime=Optional.ofNullable(file.getContentType()).orElse("").split(";")[0].toLowerCase(Locale.ROOT);
        if(file.isEmpty()||file.getSize()>10*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"录音为空或超过10MB");
        String transcript;
        try{transcript=client.transcribe(file.getBytes(),file.getOriginalFilename(),mime);}
        catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage());}
        Map<String,Object> result=feedback.evaluate(exercise.get("question"),transcript,exercise.get("reference"));
        int number=jdbc.queryForObject("SELECT COALESCE(MAX(attempt_no),0)+1 FROM sf_speaking_attempt WHERE user_id=? AND exercise_id=?",
            Integer.class,userId,exerciseId);
        jdbc.update("""
            INSERT INTO sf_speaking_attempt(user_id,exercise_id,transcript,feedback_json,feedback_mode,input_type,attempt_no)
            VALUES(?,?,?,?,?,'AUDIO',?)
            """,userId,exerciseId,transcript,mapper.writeValueAsString(result),result.get("mode"),number);
        Map<String,Object> body=new LinkedHashMap<>(result);
        body.put("transcript",transcript);
        body.put("inputType","AUDIO");
        return body;
    }
    @GetMapping("/exercises/{exerciseId}/attempts")public List<Map<String,Object>> attempts(
        @PathVariable long exerciseId,Authentication auth){
        return jdbc.query("""
            SELECT a.id,a.transcript,a.input_type,a.attempt_no,a.created_at
            FROM sf_speaking_attempt a JOIN sf_exercise e ON e.id=a.exercise_id
            JOIN sf_lesson l ON l.id=e.lesson_id JOIN sf_plan_member m ON m.plan_id=l.plan_id
            WHERE a.exercise_id=? AND a.user_id=? AND m.user_id=? ORDER BY a.created_at DESC LIMIT 20
            """,(rs,i)->Map.of("id",rs.getLong("id"),"transcript",rs.getString("transcript"),
                "inputType",rs.getString("input_type"),"attemptNo",rs.getInt("attempt_no"),
                "createdAt",rs.getTimestamp("created_at").toInstant().toString()),exerciseId,user(auth),user(auth));
    }
}
