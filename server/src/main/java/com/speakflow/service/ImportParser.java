package com.speakflow.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.*;

@Component
public class ImportParser {
    private final ObjectMapper mapper;
    public ImportParser(ObjectMapper mapper){this.mapper=mapper;}
    public List<JsonNode> parse(String source){
        if(source==null || source.isBlank() || source.length()>2_000_000)throw new IllegalArgumentException("请输入课程，最多 200 万字符");
        String text=source.strip();
        if(text.startsWith("```")){
            int line=text.indexOf('\n');
            if(line<0 || !text.endsWith("```"))throw new IllegalArgumentException("Markdown JSON 代码块不完整");
            String language=text.substring(3,line).trim();
            if(!language.isEmpty() && !language.equalsIgnoreCase("json"))throw new IllegalArgumentException("仅支持 JSON 代码块");
            text=text.substring(line+1,text.length()-3);
        }
        JsonNode input;
        try{input=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);}catch(Exception ex){throw new IllegalArgumentException("无法可靠解析自由格式文本。请将原文逐字整理成规范 JSON，或粘贴完整的 Markdown JSON 代码块；系统不会重写或补写原文。");}
        if(input==null || input.isNull())throw new IllegalArgumentException("课程不能为空");
        JsonNode list=input.has("lessons")?input.get("lessons"):input;
        List<JsonNode> result=new ArrayList<>();
        if(list.isArray()){for(JsonNode item:list)result.add(normalize(item));}
        else result.add(normalize(list));
        if(result.isEmpty() || result.size()>100)throw new IllegalArgumentException("每批应为 1–100 篇课程");
        Set<String> dates=new HashSet<>();
        for(JsonNode item:result)if(!dates.add(item.path("date").asText()))throw new IllegalArgumentException("同一批次包含重复日期："+item.path("date").asText());
        return result;
    }
    private JsonNode normalize(JsonNode item){
        if(!item.isObject())throw new IllegalArgumentException("课程必须是 JSON 对象");
        try{LocalDate.parse(item.path("date").asText());}catch(Exception ex){throw new IllegalArgumentException("课程需要有效日期 date：YYYY-MM-DD");}
        require(item,"topic",80);require(item,"level",12);
        JsonNode original=item.has("content")?item.get("content"):item;
        if(!original.isObject())throw new IllegalArgumentException("content 必须是对象");
        ObjectNode content=original.deepCopy();
        if(!content.has("pronunciationTips") && content.has("tips"))content.set("pronunciationTips",content.get("tips"));
        for(String key:List.of("words","expressions","pronunciationTips","exercises","studySteps"))if(!content.has(key))content.putArray(key);
        validate(content);
        ObjectNode out=mapper.createObjectNode();
        out.put("date",item.get("date").asText());out.put("topic",item.get("topic").asText());out.put("level",item.get("level").asText());
        out.set("content",content);out.set("original",item.deepCopy());
        return out;
    }
    public static void validate(JsonNode c){
        if(c==null || !c.isObject())throw new IllegalArgumentException("课程内容必须是对象");
        require(c,"title",255);
        array(c,"segments",1,500);
        for(JsonNode s:c.path("segments")){require(s,"en",60000);require(s,"zh",60000);}
        array(c,"words",0,500); for(JsonNode w:c.path("words")){require(w,"word",100);require(w,"meaning",255);optional(w,"ipa",150);}
        array(c,"expressions",0,500); for(JsonNode e:c.path("expressions")){require(e,"text",255);require(e,"meaning",500);optional(e,"example",60000);}
        array(c,"pronunciationTips",0,500); for(JsonNode t:c.path("pronunciationTips")){require(t,"target",500);require(t,"explanation",60000);optional(t,"ipa",500);optional(t,"example",60000);}
        array(c,"exercises",0,500); for(JsonNode e:c.path("exercises")){
            require(e,"question",60000);optional(e,"questionZh",60000);optional(e,"reference",60000);optional(e,"hint",60000);
            if(!List.of("COMPREHENSION","SUBSTITUTION","OPEN_SPEAKING").contains(e.path("type").asText()))throw new IllegalArgumentException("无效练习类型 type");
        }
        array(c,"studySteps",0,100);for(JsonNode step:c.path("studySteps"))if(!step.isTextual() || step.asText().isBlank() || step.asText().length()>10000)throw new IllegalArgumentException("studySteps 必须是原始步骤文本数组");
    }
    static void require(JsonNode n,String key,int max){if(!n.path(key).isTextual() || n.path(key).asText().isBlank())throw new IllegalArgumentException("缺少文本字段："+key);optional(n,key,max);}
    static void optional(JsonNode n,String key,int max){if(n.has(key) && (!n.get(key).isTextual() || n.get(key).asText().length()>max || (max==60000 && n.get(key).asText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000)))throw new IllegalArgumentException("字段类型或长度无效："+key);}
    static void array(JsonNode n,String key,int min,int max){JsonNode a=n.path(key);if(!a.isArray() || a.size()<min || a.size()>max)throw new IllegalArgumentException("无效数组："+key);}
}
