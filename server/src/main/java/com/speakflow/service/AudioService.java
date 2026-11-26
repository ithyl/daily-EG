package com.speakflow.service;

import com.speakflow.ai.AIConfig;
import com.speakflow.ai.OpenAIClient;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class AudioService {
    private final JdbcTemplate jdbc;
    private final LearningService learning;
    private final OpenAIClient client;
    private final AIConfig config;
    public AudioService(JdbcTemplate jdbc,LearningService learning,OpenAIClient client,AIConfig config){
        this.jdbc=jdbc;this.learning=learning;this.client=client;this.config=config;
    }

    public byte[] get(long userId,long lessonId,Long segmentId) throws Exception {
        learning.assertAccess(userId,lessonId);
        String content;
        if(segmentId!=null){
            content=jdbc.query("SELECT text_en FROM sf_lesson_segment WHERE lesson_id=? AND id=?",
                rs->rs.next()?rs.getString(1):null,lessonId,segmentId);
        }else{
            var segments=jdbc.query("SELECT text_en FROM sf_lesson_segment WHERE lesson_id=? ORDER BY segment_no",
                (rs,i)->rs.getString(1),lessonId);
            content=String.join(" ",segments);
        }
        if(content==null||content.isBlank())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"音频对应的课程段落不存在");
        String source=config.ttsModel+":"+config.voice+":"+content;
        String cacheKey=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
        Path root=Path.of(config.audioDirectory).toAbsolutePath().normalize();
        Path target=root.resolve(cacheKey+".mp3");
        if(Files.isRegularFile(target))return Files.readAllBytes(target);
        if(!config.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"未配置 AI TTS，请使用浏览器朗读功能");
        byte[] mp3=client.speech(content);
        if(mp3.length<100||mp3.length>8*1024*1024)throw new IllegalStateException("Invalid TTS data size");
        Files.createDirectories(root);
        Path tmp=Files.createTempFile(root,"tts-",".tmp");
        try{
            Files.write(tmp,mp3);
            try{Files.move(tmp,target,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.FileAlreadyExistsException ignored){
                // Another request already cached it.
            }catch(java.nio.file.AtomicMoveNotSupportedException ignored){
                Files.move(tmp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }finally{Files.deleteIfExists(tmp);}
        jdbc.update("""
            INSERT INTO sf_audio_asset(lesson_id,segment_id,cache_key,storage_path,bytes_size)
            VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE storage_path=VALUES(storage_path),bytes_size=VALUES(bytes_size)
            """,lessonId,segmentId,cacheKey,target.toString(),mp3.length);
        return Files.readAllBytes(target);
    }
}
