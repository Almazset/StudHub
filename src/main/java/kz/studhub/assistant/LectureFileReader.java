package kz.studhub.assistant;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.xml.stream.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LectureFileReader {
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 12L * 1024 * 1024;
    private static final int MAX_TEXT = 120_000;
    public record Lecture(String name, String mimeType, byte[] bytes, String text) {}

    public List<Lecture> readAll(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return List.of();
        if (files.size() > 3 || files.stream().mapToLong(MultipartFile::getSize).sum() > MAX_TOTAL_BYTES) throw error("FILE_LIMIT");
        List<Lecture> lectures = files.stream().map(this::read).toList();
        if (lectures.stream().mapToInt(file -> file.text() == null ? 0 : file.text().length()).sum() > MAX_TEXT) throw error("TEXT_LIMIT");
        return lectures;
    }

    public Lecture read(MultipartFile upload) {
        if (upload.isEmpty() || upload.getSize() > MAX_FILE_BYTES) throw error("FILE_LIMIT");
        String name = Optional.ofNullable(upload.getOriginalFilename()).orElse("lecture").replace('\\','/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (name.length() > 120) throw error("FILE_FORMAT");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = upload.getBytes();
            return switch (extension) {
                case "pdf" -> {
                    if (bytes.length < 8 || !new String(bytes,0,Math.min(1024,bytes.length),StandardCharsets.ISO_8859_1).contains("%PDF-")) throw error("FILE_FORMAT");
                    yield new Lecture(name,"application/pdf",bytes,null);
                }
                case "png", "jpg", "jpeg" -> new Lecture(name,imageType(bytes,extension),bytes,null);
                case "txt", "md" -> new Lecture(name,"text/plain",null,utf8(bytes));
                case "docx", "pptx" -> new Lecture(name,"text/plain",null,officeText(bytes,extension));
                default -> throw error("FILE_FORMAT");
            };
        } catch (IOException ex) { throw error("FILE_FORMAT"); }
    }

    private String imageType(byte[] bytes, String extension) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw error("FILE_FORMAT");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("png") && extension.equals("png") || format.equals("jpeg") && !extension.equals("png"))) throw error("FILE_FORMAT");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long)width * height > 32_000_000) throw error("FILE_LIMIT");
                return format.equals("png") ? "image/png" : "image/jpeg";
            } finally { reader.dispose(); }
        }
    }

    private String utf8(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replace("\uFEFF", "");
            if (text.length() > MAX_TEXT) throw error("TEXT_LIMIT");
            if (text.isBlank() || text.indexOf('\0') >= 0) throw error("FILE_FORMAT");
            return text;
        } catch (CharacterCodingException ex) { throw error("FILE_FORMAT"); }
    }

    private String officeText(byte[] bytes, String extension) throws IOException {
        Map<Integer,String> documents = new TreeMap<>();
        boolean expectedContent = false;
        int expanded = 0, entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 2000) throw error("FILE_LIMIT");
                String name = entry.getName();
                boolean wanted = extension.equals("docx") ? name.equals("word/document.xml") : name.matches("ppt/slides/slide[0-9]+\\.xml");
                var output = wanted ? new ByteArrayOutputStream() : null;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > 20 * 1024 * 1024 || wanted && output.size() + count > 2 * 1024 * 1024) throw error("FILE_LIMIT");
                    if (wanted) output.write(buffer,0,count);
                }
                if (wanted) {
                    expectedContent = true;
                    int index = extension.equals("docx") ? 0 : Integer.parseInt(name.replaceAll("\\D", ""));
                    if (documents.size() >= 200) throw error("FILE_LIMIT");
                    documents.put(index,xmlText(output.toByteArray()));
                }
            }
        } catch (XMLStreamException | NumberFormatException ex) { throw error("FILE_FORMAT"); }
        String text = String.join("\n\n",documents.values());
        if (!expectedContent || text.isBlank()) throw error("FILE_FORMAT");
        if (text.length() > MAX_TEXT) throw error("TEXT_LIMIT");
        return text;
    }

    private String xmlText(byte[] bytes) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
        StringBuilder text = new StringBuilder();
        boolean inText = false;
        try {
            while (reader.hasNext()) {
                int type = reader.next();
                if (type == XMLStreamConstants.DTD) throw error("FILE_FORMAT");
                if (type == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("t")) inText = true;
                if ((type == XMLStreamConstants.CHARACTERS || type == XMLStreamConstants.CDATA) && inText) text.append(reader.getText());
                if (type == XMLStreamConstants.END_ELEMENT) {
                    if (reader.getLocalName().equals("t")) { inText = false; text.append(' '); }
                    if (reader.getLocalName().equals("p")) text.append('\n');
                }
                if (text.length() > MAX_TEXT) throw error("TEXT_LIMIT");
            }
        } finally { reader.close(); }
        return text.toString().trim();
    }
    private AssistantException error(String code) { return new AssistantException(HttpStatus.BAD_REQUEST,code); }
}
