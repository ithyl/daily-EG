package com.speakflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.speakflow.service.LearningService;
import com.speakflow.service.LessonImportService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class LessonImportController {
    private final LearningService learning;
    private final LessonImportService importer;
    public LessonImportController(LearningService learning, LessonImportService importer){this.learning=learning;this.importer=importer;}
    public record ImportBody(long planId, LocalDate date, String topic, String level, JsonNode content){}
    @PostMapping("/admin/import-lesson")
    public Map<String,Object> importLesson(@RequestBody ImportBody body, Authentication auth){
        return importer.importLesson(learning.userId(auth.getName()),body.planId(),body.date(),body.topic(),body.level(),body.content());
    }

    public record PreviewBody(String source){}
    public record ConfirmBody(long planId,String source){}
    @PostMapping("/lessons/import/preview")
    public Map<String,Object> preview(@RequestBody PreviewBody body){
        return Map.of("lessons",parse(body.source()),"warnings",java.util.List.of("按原日期导入；已有日期一律跳过，不更新草稿或已发布课程。", "未提供的发音、词汇或练习将保持为空，不自动补写。"));
    }
    @PostMapping("/lessons/import")
    public Map<String,Object> confirm(@RequestBody ConfirmBody body,Authentication auth){
        return Map.of("results",importer.importBatch(learning.userId(auth.getName()),body.planId(),parse(body.source())));
    }
    private java.util.List<JsonNode> parse(String source){
        try{return new com.speakflow.service.ImportParser(new com.fasterxml.jackson.databind.ObjectMapper()).parse(source);}
        catch(IllegalArgumentException ex){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,ex.getMessage());}
    }
}
