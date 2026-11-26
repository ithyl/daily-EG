package com.speakflow.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImportParserTest {
 final ImportParser parser=new ImportParser(new ObjectMapper());
 final String lesson="""
 {"date":"2026-11-25","topic":"Work","level":"B1","title":"Original title","segments":[{"en":"  Original—sentence.\nSecond line!  ","zh":" 原译文。 "}],"studySteps":["先朗读原句，再复述。"]}
 """.replace("sentence.\nSecond", "sentence.\\nSecond");
 @Test void preservesOriginalTextAndSteps(){
  var result=parser.parse("```json\n"+lesson+"\n```").getFirst();
  assertEquals("  Original—sentence.\nSecond line!  ",result.path("content").path("segments").get(0).path("en").asText());
  assertEquals(" 原译文。 ",result.path("content").path("segments").get(0).path("zh").asText());
  assertEquals("先朗读原句，再复述。",result.path("content").path("studySteps").get(0).asText());
  assertTrue(result.path("content").path("words").isEmpty());
 }
 @Test void rejectsAmbiguousTextAndInvalidDate(){
  assertThrows(IllegalArgumentException.class,()->parser.parse("# Title\nEnglish and Chinese without mappings"));
  assertThrows(IllegalArgumentException.class,()->parser.parse(lesson.replace("2026-11-25","invalid")));
  assertThrows(IllegalArgumentException.class,()->parser.parse("null"));
  assertThrows(IllegalArgumentException.class,()->parser.parse(lesson+" trailing text"));
 }
 @Test void batchKeepsDatesAndRejectsDuplicates(){
  assertEquals(2,parser.parse("["+lesson+","+lesson.replace("2026-11-25","2026-11-24")+"]").size());
  assertThrows(IllegalArgumentException.class,()->parser.parse("["+lesson+","+lesson+"]"));
 }
 @Test void readsExistingHistoricalSeedWithoutRewriting()throws Exception{
  String source=new String(getClass().getResourceAsStream("/seed/lessons.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
  var raw=new ObjectMapper().readTree(source);var parsed=parser.parse(source);
  assertEquals(8,parsed.size());
  for(int i=0;i<8;i++)assertEquals(raw.get(i).get("segments"),parsed.get(i).path("content").get("segments"));
 }
}
