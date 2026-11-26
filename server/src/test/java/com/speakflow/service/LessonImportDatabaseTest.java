package com.speakflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opt-in; every fixture and imported course is rolled back. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="SPEAKFLOW_DB_TEST",matches="1")
class LessonImportDatabaseTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired LessonImportService importer;
 @Autowired ImportParser parser;
 @Autowired LearningService learning;
 @Test void importsOriginalContentStepsAndDeduplicatesInDatabase() throws Exception {
  String username="import-test-"+java.util.UUID.randomUUID();
  jdbc.update("INSERT INTO sf_user(username,password_hash,display_name) VALUES(?,?,?)",username,"unused-test-password-hash","Import test");
  long user=jdbc.queryForObject("SELECT id FROM sf_user WHERE username=?",Long.class,username);
  jdbc.update("INSERT INTO sf_plan(owner_user_id,title) VALUES(?,?)",user,"Transaction-only import test");
  long plan=jdbc.queryForObject("SELECT id FROM sf_plan WHERE owner_user_id=?",Long.class,user);
  jdbc.update("INSERT INTO sf_plan_member(plan_id,user_id,role) VALUES(?,?,'OWNER')",plan,user);
  var mapper=new ObjectMapper();
  var seed=mapper.readTree(getClass().getResourceAsStream("/seed/lessons.json"));
  ((com.fasterxml.jackson.databind.node.ObjectNode)seed.get(0)).putArray("studySteps").add("保留原步骤：先读，再复述。");
  var parsed=parser.parse(seed.toString());
  var imported=importer.importBatch(user,plan,parsed);
  assertEquals(8,imported.size());
  assertTrue(imported.stream().allMatch(x->"IMPORTED".equals(x.get("status"))));
  long id=((Number)imported.getFirst().get("lessonId")).longValue();
  var detail=learning.detail(user,id);
  var segments=mapper.valueToTree(detail.get("segments"));
  for(int i=0;i<segments.size();i++){
   assertEquals(seed.get(0).get("segments").get(i).get("en"),segments.get(i).get("en"));
   assertEquals(seed.get(0).get("segments").get(i).get("zh"),segments.get(i).get("zh"));
  }
  assertEquals("保留原步骤：先读，再复述。",mapper.valueToTree(detail.get("studySteps")).get(0).asText());
  assertEquals(seed.get(0),mapper.readTree(jdbc.queryForObject("SELECT import_source_json FROM sf_lesson WHERE id=?",String.class,id)));
  learning.markStep(user,id,"read",true);
  assertTrue(importer.importBatch(user,plan,parsed).stream().allMatch(x->"SKIPPED_EXISTING".equals(x.get("status"))));
  assertTrue(learning.steps(user,id).get("read"));
  assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM sf_lesson WHERE plan_id=?",Integer.class,plan));
 }
}
