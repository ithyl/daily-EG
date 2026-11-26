package com.speakflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;

@Service
public class LessonImportService {
    private final JdbcTemplate jdbc;
    private final LessonWriter writer;
    public LessonImportService(JdbcTemplate jdbc, LessonWriter writer) {
        this.jdbc=jdbc; this.writer=writer;
    }

    @Transactional
    public Map<String,Object> importLesson(long userId, long planId, LocalDate date, String topic, String level, JsonNode content) {
        if(date==null || topic==null || topic.isBlank() || topic.length()>80 || level==null || level.isBlank() || level.length()>12)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"日期、主题或级别无效");
        // Serialize imports for this plan, including concurrent retries.
        Long owner=jdbc.query("SELECT owner_user_id FROM sf_plan WHERE id=? FOR UPDATE",rs->rs.next()?rs.getLong(1):null,planId);
        if(owner==null || owner!=userId)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有计划所有者可以导入课程");
        Long existing=jdbc.query("SELECT id FROM sf_lesson WHERE plan_id=? AND lesson_date=?",rs->rs.next()?rs.getLong(1):null,planId,Date.valueOf(date));
        if(existing!=null)return Map.of("lessonId",existing,"date",date.toString(),"status","SKIPPED_EXISTING");
        try { ImportParser.validate(content); }
        catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,ex.getMessage());}
        Integer max=jdbc.queryForObject("SELECT COALESCE(MAX(day_no),0) FROM sf_lesson WHERE plan_id=?",Integer.class,planId);
        long id=writer.save(planId,date,(max==null?0:max)+1,topic,level,content,true);
        jdbc.update("UPDATE sf_lesson SET generation_source='CHATGPT_IMPORT',import_source_json=?,study_steps_json=? WHERE id=?",content.toString(),content.path("studySteps").toString(),id);
        return Map.of("lessonId",id,"date",date.toString(),"status","IMPORTED");
    }

    @Transactional
    public java.util.List<Map<String,Object>> importBatch(long userId,long planId,java.util.List<com.fasterxml.jackson.databind.JsonNode> lessons){
        var results=new java.util.ArrayList<Map<String,Object>>();
        for(var lesson:lessons){
            var result=importLesson(userId,planId,LocalDate.parse(lesson.path("date").asText()),lesson.path("topic").asText(),lesson.path("level").asText(),lesson.get("content"));
            if("IMPORTED".equals(result.get("status")))jdbc.update("UPDATE sf_lesson SET import_source_json=? WHERE id=?",lesson.get("original").toString(),result.get("lessonId"));
            results.add(result);
        }
        return results;
    }
}
