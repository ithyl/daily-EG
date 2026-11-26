package com.speakflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speakflow.ai.AIConfig;
import com.speakflow.ai.OpenAIClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class LessonGenerationService {
    private static final List<String> CATEGORIES=List.of("日常生活","职场沟通","旅行","软件开发/IT");
    private static final Pattern WORDS=Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*");
    private final JdbcTemplate jdbc;
    private final LessonWriter writer;
    private final OpenAIClient ai;
    private final AIConfig config;
    private final ObjectMapper mapper;
    public LessonGenerationService(JdbcTemplate jdbc,LessonWriter writer,OpenAIClient ai,AIConfig config,ObjectMapper mapper){
        this.jdbc=jdbc;this.writer=writer;this.ai=ai;this.config=config;this.mapper=mapper;
    }

    public List<Map<String,Object>> plans(long userId){
        return jdbc.query("""
            SELECT p.id,p.title,p.timezone,p.target_level,p.publish_time,p.generation_enabled
            FROM sf_plan p JOIN sf_plan_member m ON m.plan_id=p.id WHERE m.user_id=? ORDER BY p.id
            """,(rs,i)->{
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",rs.getLong("id"));row.put("title",rs.getString("title"));
                row.put("timezone",rs.getString("timezone"));row.put("level",rs.getString("target_level"));
                row.put("publishTime",rs.getString("publish_time"));row.put("generationEnabled",rs.getBoolean("generation_enabled"));
                return row;
            },userId);
    }

    public List<Map<String,Object>> jobs(long userId){
        return jdbc.query("""
            SELECT j.id,j.plan_id,j.lesson_date,j.status,j.message,j.started_at,j.finished_at
            FROM sf_generation_job j JOIN sf_plan_member m ON m.plan_id=j.plan_id
            WHERE m.user_id=? ORDER BY j.started_at DESC LIMIT 25
            """,(rs,i)->{
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("id",rs.getLong("id"));row.put("planId",rs.getLong("plan_id"));
                row.put("date",rs.getDate("lesson_date").toLocalDate().toString());
                row.put("status",rs.getString("status"));
                row.put("message",rs.getString("message") == null ? "" : rs.getString("message"));
                return row;
            },userId);
    }

    public Map<String,Object> generateOwned(long userId,long planId,LocalDate date) throws Exception {
        Long owner=jdbc.query("SELECT owner_user_id FROM sf_plan WHERE id=?",rs->rs.next()?rs.getLong(1):null,planId);
        if(owner==null||owner!=userId)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只有计划所有者可以生成课程");
        String zone=jdbc.queryForObject("SELECT timezone FROM sf_plan WHERE id=?",String.class,planId);
        LocalDate current=LocalDate.now(ZoneId.of(zone));
        if(date.isBefore(current.minusDays(1))||date.isAfter(current.plusDays(7)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"课程日期只能是昨天至未来7天");
        return generate(planId,date);
    }

    public Map<String,Object> generate(long planId,LocalDate date) throws Exception {
        if(!config.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"请先配置 AI_PROVIDER=openai 和 AI_API_KEY");
        Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM sf_lesson WHERE plan_id=? AND lesson_date=?",Integer.class,planId,Date.valueOf(date));
        if(exists!=null&&exists>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"该日期已有课程，避免覆盖原文和学习进度");
        String key="lesson:"+planId+":"+date;
        try{
            jdbc.update("""
                INSERT INTO sf_generation_job(plan_id,lesson_date,idempotency_key,status,provider)
                VALUES(?,?,?,'RUNNING',?)
                """,planId,Date.valueOf(date),key,config.provider);
        }catch(DuplicateKeyException duplicate){
            int resumed=jdbc.update("""
                UPDATE sf_generation_job SET status='RUNNING',message=NULL,started_at=UTC_TIMESTAMP(3),finished_at=NULL
                WHERE idempotency_key=? AND status='FAILED'
                """,key);
            if(resumed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"该课程的生成任务正在执行或已成功");
        }
        try{
            String previous=jdbc.query("""
                SELECT topic FROM sf_lesson WHERE plan_id=? ORDER BY lesson_date DESC LIMIT 1
                """,rs->rs.next()?rs.getString(1):"",planId);
            int old=CATEGORIES.indexOf(previous);
            String category=CATEGORIES.get((old+1+CATEGORIES.size())%CATEGORIES.size());
            List<String> previousTitles=jdbc.query("""
                SELECT title_en FROM sf_lesson WHERE plan_id=? ORDER BY lesson_date DESC LIMIT 20
                """,(rs,i)->rs.getString(1),planId);
            String level=jdbc.queryForObject("SELECT target_level FROM sf_plan WHERE id=?",String.class,planId);
            String prompt=mapper.writeValueAsString(Map.of("date",date.toString(),"topicCategory",category,
                "level",level,"recentTitlesToAvoid",previousTitles,
                "audience","Java backend engineer preparing for global English-speaking workplaces"));
            JsonNode content=ai.complete(SYSTEM_PROMPT,prompt);
            validate(content);
            Integer max=jdbc.queryForObject("SELECT COALESCE(MAX(day_no),0) FROM sf_lesson WHERE plan_id=?",Integer.class,planId);
            int dayNo=(max==null?0:max)+1;
            String zone=jdbc.queryForObject("SELECT timezone FROM sf_plan WHERE id=?",String.class,planId);
            java.sql.Time publishTime=jdbc.queryForObject("SELECT publish_time FROM sf_plan WHERE id=?",java.sql.Time.class,planId);
            ZonedDateTime now=ZonedDateTime.now(ZoneId.of(zone));
            boolean publish=!date.isAfter(now.toLocalDate())&&
                (!date.equals(now.toLocalDate())||!now.toLocalTime().isBefore(publishTime.toLocalTime()));
            long lessonId=writer.save(planId,date,dayNo,category,level,content,publish);
            jdbc.update("UPDATE sf_generation_job SET status='SUCCESS',message=?,finished_at=UTC_TIMESTAMP(3) WHERE idempotency_key=?",
                "课程已生成（"+(publish?"已发布":"等待到点发布")+"）",key);
            return Map.of("lessonId",lessonId,"date",date.toString(),"status",publish?"PUBLISHED":"DRAFT","day",dayNo);
        }catch(Exception ex){
            jdbc.update("UPDATE sf_generation_job SET status='FAILED',message=?,finished_at=UTC_TIMESTAMP(3) WHERE idempotency_key=?",
                "生成失败，请检查内容校验或服务端日志（"+ex.getClass().getSimpleName()+"）",key);
            throw ex;
        }
    }

    @Scheduled(cron="0 */10 * * * *",zone="UTC")
    public void scheduled(){
        if(!config.enabled())return;
        List<Map<String,Object>> active=jdbc.query("""
            SELECT id,timezone,publish_time FROM sf_plan WHERE status='ACTIVE' AND generation_enabled=1
            """,(rs,i)->Map.of("id",rs.getLong(1),"zone",rs.getString(2),"publishTime",rs.getTime(3).toLocalTime()));
        for(Map<String,Object> plan:active){
            long planId=(Long)plan.get("id");
            try{
                ZonedDateTime now=ZonedDateTime.now(ZoneId.of((String)plan.get("zone")));
                LocalDate today=now.toLocalDate();
                // Prepare the lesson from 06:30; publish when the plan's publish time arrives.
                if(!now.toLocalTime().isBefore(LocalTime.of(6,30))){
                    Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM sf_lesson WHERE plan_id=? AND lesson_date=?",
                        Integer.class,planId,Date.valueOf(today));
                    if(exists!=null&&exists==0){
                        String status=jdbc.query("SELECT status FROM sf_generation_job WHERE idempotency_key=?",
                            rs->rs.next()?rs.getString(1):"", "lesson:"+planId+":"+today);
                        if(status.isBlank())generate(planId,today); // Failed attempts need explicit manual retry.
                    }
                }
                jdbc.update("""
                    UPDATE sf_lesson SET status='PUBLISHED',published_at=UTC_TIMESTAMP(3)
                    WHERE plan_id=? AND status='DRAFT' AND generation_source='AI' AND lesson_date<=?
                    """,planId,Date.valueOf(now.toLocalTime().isBefore((LocalTime)plan.get("publishTime"))?today.minusDays(1):today));
            }catch(Exception error){
                // Keep the scheduler alive; detailed failure status is stored in sf_generation_job.
                System.err.println("SpeakFlow generation scheduler: plan="+planId+", "+error.getClass().getSimpleName());
            }
        }
    }

    static void validate(JsonNode content){
        if(content==null||!content.isObject())throw new IllegalArgumentException("Invalid AI lesson object");
        String title=content.path("title").asText("");
        if(title.length()<8||title.length()>255)throw new IllegalArgumentException("Invalid lesson title");
        JsonNode segments=content.path("segments");
        if(!segments.isArray()||segments.size()<8||segments.size()>28)throw new IllegalArgumentException("Invalid sentence count");
        int count=0;
        for(JsonNode s:segments){
            if(s.path("en").asText("").isBlank()||s.path("zh").asText("").isBlank())
                throw new IllegalArgumentException("Untranslated segment");
            var matcher=WORDS.matcher(s.path("en").asText());
            while(matcher.find())count++;
        }
        if(count<150||count>220)throw new IllegalArgumentException("Article word count "+count+" out of range");
        for(var required:List.of(Map.entry("words",8),Map.entry("expressions",5),
                                  Map.entry("pronunciationTips",2),Map.entry("exercises",6))){
            if(!content.path(required.getKey()).isArray()||content.path(required.getKey()).size()!=required.getValue())
                throw new IllegalArgumentException("Expected "+required.getValue()+" "+required.getKey());
        }
        for(JsonNode w:content.path("words"))
            if(w.path("word").asText("").isBlank() || w.path("meaning").asText("").isBlank())
                throw new IllegalArgumentException("Empty vocabulary item");
        for(JsonNode e:content.path("expressions"))
            if(e.path("text").asText("").isBlank() || e.path("meaning").asText("").isBlank() || e.path("example").asText("").isBlank())
                throw new IllegalArgumentException("Incomplete expression");
        for(JsonNode e:content.path("pronunciationTips"))
            if(e.path("target").asText("").isBlank() || e.path("explanation").asText("").isBlank())
                throw new IllegalArgumentException("Incomplete pronunciation tip");
        for(JsonNode e:content.path("exercises"))
            if(e.path("question").asText("").isBlank() || e.path("questionZh").asText("").isBlank() || e.path("reference").asText("").isBlank() ||
                !List.of("COMPREHENSION","SUBSTITUTION","OPEN_SPEAKING").contains(e.path("type").asText()))
                throw new IllegalArgumentException("Incomplete exercise");
    }

    private static final String SYSTEM_PROMPT="""
        You create ORIGINAL daily English-speaking lessons for a Chinese-speaking Java engineer at CEFR B1+ to B2.
        Output strictly a JSON object, with no markdown or commentary. Use these exact keys:
        title: natural English title (8-100 characters);
        segments: 12-22 objects {en: single English sentence or short spoken sentence, zh: faithful natural Chinese translation};
        words: exactly 8 objects {word, ipa, meaning};
        expressions: exactly 5 objects {text, meaning, example};
        pronunciationTips: exactly 2 objects {target, ipa, explanation, example};
        exercises: exactly 6 objects {type, question, questionZh, reference, hint}.
        The article must be 150-220 English words in total. Segments joined in order form ONE coherent story.
        For exercises use the type values COMPREHENSION twice, SUBSTITUTION twice, and OPEN_SPEAKING twice.
        Make Chinese translations faithful and nuanced. Use American pronunciation IPA for eight difficult words,
        but do not pretend IPA is verified. Avoid repeating recent topics. Be relevant to the topicCategory.
        Include a realistic speaking task that asks learners to explain something in their own words.
        Never include personal identifying information, real credentials, or placeholders resembling secrets.
        """;
}
