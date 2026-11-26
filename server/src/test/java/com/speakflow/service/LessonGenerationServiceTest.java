package com.speakflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LessonGenerationServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    String validContent() throws Exception {
        var root=mapper.createObjectNode();root.put("title","How to Explain a Technical Trade-off");
        var segments=root.putArray("segments");
        for(int i=0;i<12;i++){
            var s=segments.addObject();
            s.put("en","I explained the main advantages and possible risks before we agreed on a practical next step together.");
            s.put("zh","我解释了优点和风险，然后我们一起确定了下一步。");
        }
        var words=root.putArray("words");
        for(int i=0;i<8;i++){var w=words.addObject();w.put("word","practical");w.put("ipa","/ˈpræktɪkəl/");w.put("meaning","实用的");}
        var expressions=root.putArray("expressions");
        for(int i=0;i<5;i++){var x=expressions.addObject();x.put("text","work on");x.put("meaning","从事");x.put("example","I work on a project.");}
        var tips=root.putArray("pronunciationTips");
        for(int i=0;i<2;i++){var x=tips.addObject();x.put("target","work on");x.put("explanation","注意连读");}
        var exercises=root.putArray("exercises");
        for(int i=0;i<6;i++){var x=exercises.addObject();x.put("type","OPEN_SPEAKING");x.put("question","What did you do?");x.put("questionZh","你做了什么？");x.put("reference","I explained the risks.");}
        return root.toString();
    }
    @Test void validLessonShouldPass() throws Exception {
        JsonNode node=mapper.readTree(validContent());
        assertDoesNotThrow(()->LessonGenerationService.validate(node));
    }
    @Test void missingTranslationMustFail() throws Exception {
        JsonNode node=mapper.readTree(validContent());
        ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("segments").get(0)).put("zh","");
        assertThrows(IllegalArgumentException.class,()->LessonGenerationService.validate(node));
    }
    @Test void outOfRangeWordCountMustFail() throws Exception {
        JsonNode node=mapper.readTree(validContent());
        for(JsonNode segment:node.path("segments"))
            ((com.fasterxml.jackson.databind.node.ObjectNode)segment).put("en","Hi.");
        assertThrows(IllegalArgumentException.class,()->LessonGenerationService.validate(node));
    }
}
