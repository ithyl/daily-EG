package com.speakflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LessonImportServiceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final LessonWriter writer=mock(LessonWriter.class);
    final LessonImportService service=new LessonImportService(jdbc,writer);
    @Test void anotherOwnerCannotImport(){
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<ResultSetExtractor<Long>>any(),eq(1L))).thenReturn(2L);
        var error=assertThrows(ResponseStatusException.class,()->service.importLesson(1,1,LocalDate.now(),"Work","B1",null));
        assertEquals(403,error.getStatusCode().value());
        verifyNoInteractions(writer);
    }
    @Test void existingDateIsSkippedWithoutReplacingContent(){
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<ResultSetExtractor<Long>>any(),eq(1L))).thenReturn(1L);
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<ResultSetExtractor<Long>>any(),eq(1L),any(java.sql.Date.class))).thenReturn(42L);
        var result=service.importLesson(1,1,LocalDate.now(),"Work","B1",null);
        assertEquals("SKIPPED_EXISTING",result.get("status"));
        assertEquals(42L,result.get("lessonId"));
        verifyNoInteractions(writer);
    }
    @Test void invalidContentIsRejectedBeforeWriting(){
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<ResultSetExtractor<Long>>any(),eq(1L))).thenReturn(1L);
        var error=assertThrows(ResponseStatusException.class,()->service.importLesson(1,1,LocalDate.now(),"Work","B1",null));
        assertEquals(400,error.getStatusCode().value());
        verifyNoInteractions(writer);
    }
}
