package kz.studhub.chat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class ChatFileValidatorTest {
    static byte[] image(String format) throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(12, 9, BufferedImage.TYPE_INT_RGB), format, output);
            return output.toByteArray();
        }
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] docx() throws Exception {
        return zip(Map.of(
            "[Content_Types].xml", bytes("""
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """),
            "_rels/.rels", bytes("""
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>
                """),
            "word/document.xml", bytes("""
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body><w:p><w:r><w:t>StudHub</w:t></w:r></w:p></w:body>
                </w:document>
                """)));
    }

    private static byte[] pdf(boolean withAction) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            if (withAction) document.getDocumentCatalog().setOpenAction(new PDActionJavaScript(""));
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static ChatFileValidator.File validate(String name, byte[] data) {
        return ChatFileValidator.read(new MockMultipartFile("file", name, "application/octet-stream", data));
    }

    private static void rejected(String name, byte[] data, String code) {
        assertThatThrownBy(() -> validate(name,data)).isInstanceOfSatisfying(ChatService.Failure.class,
            failure -> assertThat(failure.code).isEqualTo(code));
    }

    @Test void verifiesAllSupportedFormatsFromBytes() throws Exception {
        var png = image("png");
        assertThat(validate("Лекция.png",png).mediaType()).isEqualTo("image/png");
        assertThat(validate("photo.jpeg",image("jpeg")).mediaType()).isEqualTo("image/jpeg");
        assertThat(validate("notes.pdf",pdf(false)).mediaType()).isEqualTo("application/pdf");
        assertThat(validate("notes.docx",docx()).mediaType()).endsWith("wordprocessingml.document");
        assertThat(validate("course.zip",zip(Map.of("week 1/photo.png",png,"week 1/notes.docx",docx()))).mediaType())
            .isEqualTo("application/zip");
    }

    @Test void rejectsIncorrectContentUnsafeNamesAndOversizedFiles() throws Exception {
        byte[] png = image("png");
        for (String name : new String[]{"../photo.png", "C:\\photo.png", "CON.png", "note\u202E.png", "line\nname.png"}) {
            rejected(name,png,"FILE_NAME");
        }
        rejected("image.gif",png,"FILE_TYPE");
        rejected("image.pdf",png,"FILE_CONTENT");
        rejected("image.jpg",png,"FILE_CONTENT");
        rejected("missing.pdf",bytes("%PDF-1.7"),"FILE_CONTENT");
        rejected("active.pdf",pdf(true),"FILE_CONTENT");
        rejected("large.png",new byte[ChatFileValidator.MAX_BYTES+1],"FILE_LIMIT");
    }

    @Test void rejectsArchivePathsUnsupportedEntriesAndExpandedSizeOverBudget() throws Exception {
        var png = image("png");
        rejected("unsafe.zip",zip(Map.of("../photo.png",png)),"FILE_NAME");
        rejected("mixed.zip",zip(Map.of("photo.png",png,"notes.html",bytes("notes"))),"FILE_TYPE");
        rejected("nested.zip",zip(Map.of("inner.zip",zip(Map.of("photo.png",png)))),"FILE_CONTENT");
        rejected("large.zip",zip(Map.of("photo.png",new byte[ChatFileValidator.MAX_BYTES+1])),"FILE_LIMIT");
        rejected("not-a-doc.docx",zip(Map.of("word/document.xml",bytes("<document/>"))),"FILE_CONTENT");
    }
}
