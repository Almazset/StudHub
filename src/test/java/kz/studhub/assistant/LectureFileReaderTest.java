package kz.studhub.assistant;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class LectureFileReaderTest {
    private final LectureFileReader reader = new LectureFileReader();
    private MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("files",name,"application/octet-stream",bytes); }
    @Test void readsUnicodeTextAndRejectsBinaryContentInTextFiles() {
        String text = "Лекция: коллекции Java. Тізімдер мен жиындар.";
        assertEquals(text,reader.read(file("lecture.txt",text.getBytes(StandardCharsets.UTF_8))).text());
        assertThrows(AssistantException.class,() -> reader.read(file("invalid.txt",new byte[]{(byte)0xff,0})));
        assertThrows(AssistantException.class,() -> reader.read(file("large.txt","x".repeat(120001).getBytes(StandardCharsets.UTF_8))));
    }
    @Test void rejectsRenamedExecutablesAndEnforcesFileAndBatchLimits() {
        assertThrows(AssistantException.class,() -> reader.read(file("lecture.pdf","<script>bad</script>".getBytes())));
        assertThrows(AssistantException.class,() -> reader.read(file("lecture.png","<svg onload='bad()'/>".getBytes())));
        assertThrows(AssistantException.class,() -> reader.read(file("lecture.exe",new byte[]{1,2,3})));
        assertThrows(AssistantException.class,() -> reader.read(file("large.pdf",new byte[(int)LectureFileReader.MAX_FILE_BYTES+1])));
        var note = file("lecture.txt","Some lecture".getBytes());
        assertThrows(AssistantException.class,() -> reader.readAll(List.of(note,note,note,note)));
    }
    @Test void extractsDocxTextAndPptxSlidesInNumericOrder() throws Exception {
        var docx = archive(Map.of("word/document.xml","<document><p><t>Java collections</t></p><p><t>Lists and sets</t></p></document>"));
        assertEquals("Java collections \nLists and sets",reader.read(file("lecture.docx",docx)).text());
        var pptx = archive(Map.of("ppt/slides/slide10.xml","<slide><p><t>Tenth slide</t></p></slide>","ppt/slides/slide2.xml","<slide><p><t>Second slide</t></p></slide>"));
        String text = reader.read(file("lecture.pptx",pptx)).text();
        assertTrue(text.indexOf("Second slide") < text.indexOf("Tenth slide"));
    }
    @Test void rejectsExternalEntitiesAndDecompressionBombs() throws Exception {
        var malicious = archive(Map.of("word/document.xml","<!DOCTYPE x [<!ENTITY ext SYSTEM 'file:///etc/passwd'>]><document><p><t>&ext;</t></p></document>"));
        assertThrows(AssistantException.class,() -> reader.read(file("bad.docx",malicious)));
        var bomb = archive(Map.of("word/document.xml","<document><t>"+"x".repeat(2*1024*1024)+"</t></document>"));
        assertThrows(AssistantException.class,() -> reader.read(file("bomb.docx",bomb)));
    }
    private byte[] archive(Map<String,String> entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
        }
        return output.toByteArray();
    }
}
