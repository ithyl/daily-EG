package com.speakflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Date;
import java.time.LocalDate;

@Service
public class LessonWriter {
    private final JdbcTemplate jdbc;
    public LessonWriter(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public long save(long planId,LocalDate date,int dayNo,String topic,String level,JsonNode doc,boolean publish){
        long lessonId=insert("""
            INSERT INTO sf_lesson(plan_id,lesson_date,day_no,title_en,topic,level,status,generation_source,published_at)
            VALUES(?,?,?,?,?,?,?,'AI',IF(?='PUBLISHED',UTC_TIMESTAMP(3),NULL))
            """,planId,Date.valueOf(date),dayNo,doc.path("title").asText(),topic,level,
            publish?"PUBLISHED":"DRAFT",publish?"PUBLISHED":"DRAFT");
        int no=0;
        for(JsonNode s:doc.path("segments")) jdbc.update("INSERT INTO sf_lesson_segment(lesson_id,segment_no,text_en,text_zh) VALUES(?,?,?,?)",
            lessonId,++no,s.path("en").asText(),s.path("zh").asText());
        no=0;
        for(JsonNode w:doc.path("words")) jdbc.update("""
            INSERT INTO sf_lesson_word(lesson_id,sort_order,word,ipa,meaning_zh) VALUES(?,?,?,?,?)
            """,lessonId,++no,w.path("word").asText(),w.path("ipa").asText(),w.path("meaning").asText());
        no=0;
        for(JsonNode e:doc.path("expressions")) jdbc.update("""
            INSERT INTO sf_lesson_expression(lesson_id,sort_order,expression_en,meaning_zh,example_en) VALUES(?,?,?,?,?)
            """,lessonId,++no,e.path("text").asText(),e.path("meaning").asText(),e.path("example").asText());
        no=0;
        for(JsonNode e:doc.path("pronunciationTips")) jdbc.update("""
            INSERT INTO sf_pronunciation_tip(lesson_id,sort_order,target_text,ipa_us,explanation_zh,example_en) VALUES(?,?,?,?,?,?)
            """,lessonId,++no,e.path("target").asText(),e.path("ipa").asText(),e.path("explanation").asText(),e.path("example").asText());
        no=0;
        for(JsonNode e:doc.path("exercises")) jdbc.update("""
            INSERT INTO sf_exercise(lesson_id,sort_order,exercise_type,question_en,question_zh,reference_answer,hint_en)
            VALUES(?,?,?,?,?,?,?)
            """,lessonId,++no,e.path("type").asText(),e.path("question").asText(),e.path("questionZh").asText(),
            e.path("reference").asText(),e.path("hint").asText());
        return lessonId;
    }

    private long insert(String sql,Object... params){
        GeneratedKeyHolder holder=new GeneratedKeyHolder();
        jdbc.update(connection->{
            PreparedStatement statement=connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<params.length;i++)statement.setObject(i+1,params[i]);
            return statement;
        },holder);
        if(holder.getKey()==null)throw new IllegalStateException("Missing generated ID");
        return holder.getKey().longValue();
    }
}
