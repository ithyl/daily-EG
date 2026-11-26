package com.speakflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Date;
import java.time.LocalDate;

@Component
public class BootstrapSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ObjectMapper mapper;
    @Value("${app.admin.username:admin}") String adminUsername;
    @Value("${app.admin.password:}") String adminPassword;

    public BootstrapSeeder(JdbcTemplate jdbc, PasswordEncoder encoder, ObjectMapper mapper) {
        this.jdbc=jdbc; this.encoder=encoder; this.mapper=mapper;
    }

    @Override @Transactional public void run(ApplicationArguments args) throws Exception {
        Long userId = jdbc.query("SELECT id FROM sf_user WHERE username=?", rs -> rs.next()?rs.getLong(1):null, adminUsername);
        if (userId==null) {
            if (adminPassword.isBlank() || adminPassword.length()<12)
                throw new IllegalStateException("APP_ADMIN_PASSWORD must be at least 12 characters to create the initial user");
            userId = insert("INSERT INTO sf_user(username,password_hash,display_name) VALUES(?,?,?)",
                adminUsername, encoder.encode(adminPassword), "SpeakFlow Learner");
        }
        Long planId = jdbc.query("SELECT id FROM sf_plan WHERE owner_user_id=? ORDER BY id LIMIT 1", rs -> rs.next()?rs.getLong(1):null, userId);
        if (planId==null) {
            planId=insert("INSERT INTO sf_plan(owner_user_id,title) VALUES(?,?)",userId,"每日英语口语练习");
        }
        jdbc.update("INSERT IGNORE INTO sf_plan_member(plan_id,user_id,role) VALUES(?,?,'OWNER')",planId,userId);
        JsonNode lessons = mapper.readTree(new ClassPathResource("seed/lessons.json").getInputStream());
        for (JsonNode lesson : lessons) {
            String date=lesson.path("date").asText();
            Long existing=jdbc.query("SELECT id FROM sf_lesson WHERE plan_id=? AND lesson_date=?", rs->rs.next()?rs.getLong(1):null,planId,Date.valueOf(LocalDate.parse(date)));
            if (existing!=null) continue;
            Long lessonId=insert("INSERT INTO sf_lesson(plan_id,lesson_date,day_no,title_en,topic,level,status) VALUES(?,?,?,?,?,?,'PUBLISHED')",
                planId,Date.valueOf(LocalDate.parse(date)),lesson.path("day").asInt(),lesson.path("title").asText(),lesson.path("topic").asText(),lesson.path("level").asText());
            int i=0;
            for(JsonNode s:lesson.path("segments")) jdbc.update("INSERT INTO sf_lesson_segment(lesson_id,segment_no,text_en,text_zh) VALUES(?,?,?,?)",
                lessonId,++i,s.path("en").asText(),s.path("zh").asText());
            i=0;
            for(JsonNode w:lesson.path("words")) jdbc.update("INSERT INTO sf_lesson_word(lesson_id,sort_order,word,ipa,meaning_zh) VALUES(?,?,?,?,?)",
                lessonId,++i,w.path("word").asText(),w.path("ipa").asText(),w.path("meaning").asText());
            i=0;
            for(JsonNode e:lesson.path("expressions")) jdbc.update("INSERT INTO sf_lesson_expression(lesson_id,sort_order,expression_en,meaning_zh,example_en) VALUES(?,?,?,?,?)",
                lessonId,++i,e.path("text").asText(),e.path("meaning").asText(),e.path("example").asText());
            i=0;
            for(JsonNode e:lesson.path("exercises")) jdbc.update("INSERT INTO sf_exercise(lesson_id,sort_order,exercise_type,question_en,question_zh,reference_answer) VALUES(?,?,?,?,?,?)",
                lessonId,++i,e.path("type").asText(),e.path("question").asText(),e.path("questionZh").asText(),e.path("reference").asText());
        }
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) connection -> {
            PreparedStatement statement = connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            return statement;
        },holder);
        if(holder.getKey()==null) throw new IllegalStateException("Database insert did not return an id");
        return holder.getKey().longValue();
    }
}
