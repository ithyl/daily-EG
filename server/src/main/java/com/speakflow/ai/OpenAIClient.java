package com.speakflow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OpenAIClient {
    private final AIConfig config;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    public OpenAIClient(AIConfig config, ObjectMapper mapper) {this.config=config;this.mapper=mapper;}

    public JsonNode complete(String system, String user) throws Exception {
        requireEnabled();
        String body=mapper.writeValueAsString(Map.of("model",config.model,
            "response_format",Map.of("type","json_object"),
            "messages",List.of(Map.of("role","system","content",system), Map.of("role","user","content",user))));
        byte[] raw=post("/v1/chat/completions", "application/json",body.getBytes(StandardCharsets.UTF_8), 75);
        JsonNode outer=mapper.readTree(raw);
        String content=outer.path("choices").path(0).path("message").path("content").asText();
        if(content==null||content.isBlank())throw new IllegalStateException("AI returned empty content");
        return mapper.readTree(content);
    }

    public byte[] speech(String text) throws Exception {
        requireEnabled();
        if(text.isBlank() || text.length()>4000) throw new IllegalArgumentException("Invalid TTS length");
        String body=mapper.writeValueAsString(Map.of("model",config.ttsModel,"input",text,"voice",config.voice,
            "response_format","mp3"));
        return post("/v1/audio/speech","application/json",body.getBytes(StandardCharsets.UTF_8),75);
    }

    public String transcribe(byte[] bytes,String originalFilename,String mime) throws Exception {
        requireEnabled();
        if(bytes.length<100 || bytes.length>10*1024*1024)throw new IllegalArgumentException("Audio size out of range");
        if(!List.of("audio/webm","audio/mp4","audio/mpeg","audio/wav","audio/ogg","audio/x-wav").contains(mime))
            throw new IllegalArgumentException("Unsupported audio format");
        String extension=switch(mime){case "audio/webm"->"webm";case "audio/mp4"->"mp4";case "audio/mpeg"->"mp3";case "audio/ogg"->"ogg";default->"wav";};
        String boundary="speakflow-"+UUID.randomUUID().toString().replace("-","");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        append(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n"+config.asrModel+"\r\n");
        append(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\nen\r\n");
        append(out,"--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speaking."+extension+"\"\r\nContent-Type: "+mime+"\r\n\r\n");
        out.write(bytes);append(out,"\r\n--"+boundary+"--\r\n");
        byte[] response=post("/v1/audio/transcriptions","multipart/form-data; boundary="+boundary,out.toByteArray(),90);
        JsonNode result=mapper.readTree(response);
        String transcript=result.path("text").asText("").trim();
        if(transcript.isBlank())throw new IllegalStateException("ASR returned empty transcript");
        return transcript;
    }
    private static void append(ByteArrayOutputStream stream,String s) throws Exception {stream.write(s.getBytes(StandardCharsets.UTF_8));}
    private byte[] post(String path,String contentType,byte[] body,int timeout) throws Exception {
        String base=config.baseUrl.replaceAll("/+$","");
        URI endpoint=URI.create(base+path);
        if(!"https".equalsIgnoreCase(endpoint.getScheme()))throw new IllegalStateException("AI API requires HTTPS");
        HttpRequest request=HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(timeout)).header("Authorization","Bearer "+config.key)
            .header("Content-Type",contentType).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<byte[]> response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()<200 || response.statusCode()>299)
            throw new IllegalStateException("AI provider returned HTTP "+response.statusCode());
        return response.body();
    }
    private void requireEnabled(){if(!config.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "尚未配置 AI 服务：请设置 AI_PROVIDER=openai 和 AI_API_KEY");}
}
