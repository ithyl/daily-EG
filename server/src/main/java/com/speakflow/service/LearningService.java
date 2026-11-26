package com.speakflow.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class LearningService {
    private final JdbcTemplate jdbc;
    public LearningService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public long userId(String username){
        Long id=jdbc.query("SELECT id FROM sf_user WHERE username=?", rs->rs.next()?rs.getLong(1):null,username);
        if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return id;
    }

    private Map<String,Object> lessonRow(ResultSet rs) throws SQLException {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("id",rs.getLong("id"));
        m.put("day",rs.getInt("day_no"));
        m.put("date",rs.getDate("lesson_date").toLocalDate().toString());
        m.put("title",rs.getString("title_en"));
        m.put("topic",rs.getString("topic"));
        m.put("level",rs.getString("level"));
        return m;
    }
    public List<Map<String,Object>> list(long userId){
        return jdbc.query("""
            SELECT l.* FROM sf_lesson l JOIN sf_plan_member pm ON pm.plan_id=l.plan_id
            WHERE pm.user_id=? AND l.status='PUBLISHED' ORDER BY l.lesson_date DESC
            """,(rs,i)->lessonRow(rs),userId);
    }
    public void assertAccess(long userId,long lessonId){
        Integer count=jdbc.queryForObject("""
            SELECT COUNT(*) FROM sf_lesson l JOIN sf_plan_member pm ON pm.plan_id=l.plan_id
            WHERE l.id=? AND pm.user_id=? AND l.status='PUBLISHED'
            """,Integer.class,lessonId,userId);
        if(count==null || count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Lesson not found");
    }
    public Map<String,Object> detail(long userId,long lessonId){
        assertAccess(userId,lessonId);
        Map<String,Object> root=jdbc.queryForObject("SELECT * FROM sf_lesson WHERE id=?",(rs,i)->lessonRow(rs),lessonId);
        root.put("segments",jdbc.query("SELECT id,text_en,text_zh FROM sf_lesson_segment WHERE lesson_id=? ORDER BY segment_no",
            (rs,i)->Map.of("id",rs.getLong("id"),"en",rs.getString("text_en"),"zh",rs.getString("text_zh")),lessonId));
        root.put("words",jdbc.query("SELECT id,word,ipa,meaning_zh FROM sf_lesson_word WHERE lesson_id=? ORDER BY sort_order",
            (rs,i)->Map.of("id",rs.getLong("id"),"word",rs.getString("word"),"ipa",rs.getString("ipa"),"meaning",rs.getString("meaning_zh")),lessonId));
        root.put("expressions",jdbc.query("SELECT id,expression_en,meaning_zh,example_en FROM sf_lesson_expression WHERE lesson_id=? ORDER BY sort_order",
            (rs,i)->Map.of("id",rs.getLong("id"),"text",rs.getString("expression_en"),"meaning",rs.getString("meaning_zh"),"example",rs.getString("example_en")),lessonId));
        root.put("exercises",jdbc.query("SELECT id,exercise_type,question_en,question_zh FROM sf_exercise WHERE lesson_id=? ORDER BY sort_order",
            (rs,i)->Map.of("id",rs.getLong("id"),"type",rs.getString("exercise_type"),"question",rs.getString("question_en"),"questionZh",rs.getString("question_zh")),lessonId));
        root.put("tips",jdbc.query("SELECT id,target_text,ipa_us,explanation_zh,example_en FROM sf_pronunciation_tip WHERE lesson_id=? ORDER BY sort_order",
            (rs,i)->{
                Map<String,Object> tip=new LinkedHashMap<>();
                tip.put("id",rs.getLong("id"));tip.put("target",rs.getString("target_text"));
                tip.put("ipa",rs.getString("ipa_us"));tip.put("explanation",rs.getString("explanation_zh"));
                tip.put("example",rs.getString("example_en"));return tip;
            },lessonId));
        String instructions=jdbc.queryForObject("SELECT study_steps_json FROM sf_lesson WHERE id=?",String.class,lessonId);
        try { root.put("studySteps",instructions==null?java.util.List.of():new com.fasterxml.jackson.databind.ObjectMapper().readTree(instructions)); }
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid stored study steps",ex);}
        root.put("steps",steps(userId,lessonId));
        return root;
    }
    public Map<String,Boolean> steps(long userId,long lessonId){
        Map<String,Boolean> result=new LinkedHashMap<>();
        for(String step:List.of("read","vocab","shadow","replace","speak"))result.put(step,false);
        jdbc.query("SELECT step,completed FROM sf_step_progress WHERE user_id=? AND lesson_id=?",
            rs->{while(rs.next())result.put(rs.getString("step"),rs.getBoolean("completed"));return null;},userId,lessonId);
        return result;
    }
    public Map<String,Object> markStep(long userId,long lessonId,String step,boolean completed){
        assertAccess(userId,lessonId);
        if(!List.of("read","vocab","shadow","replace","speak").contains(step))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown step");
        jdbc.update("""
            INSERT INTO sf_step_progress(user_id,lesson_id,step,completed) VALUES(?,?,?,?)
            ON DUPLICATE KEY UPDATE completed=VALUES(completed),updated_at=CURRENT_TIMESTAMP(3)
            """,userId,lessonId,step,completed);
        return Map.of("steps",steps(userId,lessonId));
    }
    public Map<String,Object> stats(long userId){
        Integer lessonCount=jdbc.queryForObject("""
            SELECT COUNT(*) FROM sf_lesson l JOIN sf_plan_member pm ON pm.plan_id=l.plan_id
            WHERE pm.user_id=? AND l.status='PUBLISHED'
            """,Integer.class,userId);
        Integer done=jdbc.queryForObject("""
            SELECT COUNT(*) FROM (
            SELECT lesson_id FROM sf_step_progress WHERE user_id=? AND completed=1 GROUP BY lesson_id HAVING COUNT(DISTINCT step)=5
            ) complete_lessons
            """,Integer.class,userId);
        Integer attempts=jdbc.queryForObject("SELECT COUNT(*) FROM sf_speaking_attempt WHERE user_id=?",Integer.class,userId);
        Integer cards=jdbc.queryForObject("SELECT COUNT(*) FROM sf_review_card WHERE user_id=?",Integer.class,userId);
        return Map.of("availableLessons",lessonCount,"completedLessons",done,"attempts",attempts,"savedWords",cards);
    }
    public List<Map<String,Object>> dueCards(long userId){
        return jdbc.query("""
            SELECT c.id,w.word,w.ipa,w.meaning_zh AS meaning FROM sf_review_card c
            JOIN sf_lesson_word w ON w.id=c.word_id
            WHERE c.user_id=? AND c.next_review_at<=UTC_TIMESTAMP(3)
            ORDER BY c.next_review_at LIMIT 40
            """,(rs,i)->Map.of("id",rs.getLong("id"),"word",rs.getString("word"),
                "ipa",rs.getString("ipa"),"meaning",rs.getString("meaning")),userId);
    }
    public void saveWord(long userId,long wordId){
        Integer allowed=jdbc.queryForObject("""
            SELECT COUNT(*) FROM sf_lesson_word w JOIN sf_lesson l ON l.id=w.lesson_id
            JOIN sf_plan_member m ON m.plan_id=l.plan_id WHERE w.id=? AND m.user_id=?
            """,Integer.class,wordId,userId);
        if(allowed==null || allowed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        jdbc.update("INSERT IGNORE INTO sf_review_card(user_id,word_id) VALUES(?,?)",userId,wordId);
    }
    public void gradeWord(long userId,long cardId,boolean remembered){
        int n=jdbc.update("""
            UPDATE sf_review_card SET
              remembered_count=CASE WHEN ? THEN remembered_count+1 ELSE 0 END,
              interval_days=CASE WHEN NOT ? THEN 1 WHEN remembered_count=0 THEN 3 WHEN remembered_count=1 THEN 7 ELSE 14 END,
              next_review_at=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL (CASE WHEN NOT ? THEN 1 WHEN remembered_count=0 THEN 3 WHEN remembered_count=1 THEN 7 ELSE 14 END) DAY)
            WHERE id=? AND user_id=?
            """,remembered,remembered,remembered,cardId,userId);
        if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
