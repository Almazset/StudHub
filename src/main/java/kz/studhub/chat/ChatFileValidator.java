package kz.studhub.chat;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.xml.stream.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

final class ChatFileValidator {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final long USER_QUOTA = 64L * 1024 * 1024;
    private static final int MAX_EXPANDED = 24 * 1024 * 1024;
    private static final int MAX_ENTRIES = 256;
    private static final Semaphore VALIDATION_SLOTS = new Semaphore(2);
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Set<String> PDF_BLOCKED_KEYS = Set.of("A", "AA", "OpenAction", "JS", "JavaScript",
        "AcroForm", "XFA", "Annots", "EmbeddedFiles", "EF", "AF", "RichMediaContent", "Collection", "Launch", "AFRelationship");
    private static final Set<String> DOCX_BLOCKED_ELEMENTS = Set.of("object", "control", "altChunk", "subDoc",
        "instrText", "fldSimple", "delInstrText", "movie", "oleObject");
    private static final Set<String> DOCX_REL_TYPES = Set.of("officeDocument", "core-properties", "extended-properties",
        "custom-properties", "styles", "stylesWithEffects", "settings", "webSettings", "fontTable", "theme",
        "numbering", "header", "footer", "footnotes", "endnotes", "comments", "image", "thumbnail", "customXml", "customXmlProps");

    record File(String name, String mediaType, byte[] data) {}

    private ChatFileValidator() {}

    static File read(MultipartFile upload) {
        if (upload.isEmpty() || upload.getSize() > MAX_BYTES) throw limit();
        if (!VALIDATION_SLOTS.tryAcquire()) throw new ChatService.Failure(HttpStatus.TOO_MANY_REQUESTS, "FILE_BUSY");
        try (var input = upload.getInputStream()) {
            String name = safeName(upload.getOriginalFilename());
            byte[] data = input.readNBytes(MAX_BYTES + 1);
            if (data.length == 0 || data.length > MAX_BYTES) throw limit();
            return validate(name, data, true);
        } catch (IOException | XMLStreamException | IllegalArgumentException ex) {
            throw invalid();
        } finally {
            VALIDATION_SLOTS.release();
        }
    }

    private static File validate(String name, byte[] data, boolean allowZip) throws IOException, XMLStreamException {
        return switch (extension(name)) {
            case "png" -> new File(name, "image/png", image(data, "png"));
            case "jpg", "jpeg" -> new File(name, "image/jpeg", image(data, "jpeg"));
            case "pdf" -> new File(name, "application/pdf", pdf(data));
            case "docx" -> new File(name, DOCX, archive(data, true));
            case "zip" -> {
                if (!allowZip) throw invalid();
                yield new File(name, "application/zip", archive(data, false));
            }
            default -> throw new ChatService.Failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE_TYPE");
        };
    }

    private static String safeName(String raw) {
        if (raw == null) throw unsafeName();
        String name = Normalizer.normalize(raw, Normalizer.Form.NFC);
        if (name.length() > 120 || !name.matches("[\\p{L}\\p{N}_()\\-\\[\\]][\\p{L}\\p{M}\\p{N} ._()\\-\\[\\]]*")
                || !name.equals(name.stripTrailing()) || name.endsWith(".") || name.contains("..")
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9¹²³]|LPT[0-9¹²³])(?:[ .].*)?")) {
            throw unsafeName();
        }
        return name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] image(byte[] data, String format) throws IOException {
        var readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) throw invalid();
        var reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0), height = reader.getHeight(0);
            if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 8_000_000)
                throw limit();
            BufferedImage decoded = reader.read(0);
            try (var output = new BoundedOutput()) {
                if (!ImageIO.write(decoded, format, output)) throw invalid();
                return output.bytes();
            } finally {
                decoded.flush();
            }
        } finally {
            reader.dispose();
        }
    }

    private static byte[] pdf(byte[] data) throws IOException {
        if (data.length < 8 || !new String(data, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw invalid();
        try (var document = Loader.loadPDF(data); var output = new BoundedOutput()) {
            if (document.isEncrypted()) throw invalid();
            var cos = document.getDocument();
            if (cos.getXrefTable().size() > 20_000) throw limit();
            rejectActivePdf(cos.getTrailer());
            int pages = document.getNumberOfPages();
            if (pages < 1 || pages > 300) throw limit();
            document.save(output);
            return output.bytes();
        }
    }

    private static void rejectActivePdf(COSBase root) throws IOException {
        var pending = new ArrayDeque<COSBase>();
        var visited = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
        pending.push(root);
        while (!pending.isEmpty()) {
            var value = pending.pop();
            if (!visited.add(value)) continue;
            if (visited.size() + pending.size() > 100_000) throw limit();
            if (value instanceof COSObject object) {
                if (object.getObject() != null) pending.push(object.getObject());
            } else if (value instanceof COSDictionary dictionary) {
                if (dictionary instanceof COSStream && dictionary.containsKey(COSName.F)) throw invalid();
                var type = dictionary.getCOSName(COSName.TYPE);
                if (COSName.getPDFName("Filespec").equals(type) || COSName.getPDFName("EmbeddedFile").equals(type))
                    throw invalid();
                for (var entry : dictionary.entrySet()) {
                    if (PDF_BLOCKED_KEYS.contains(entry.getKey().getName())) throw invalid();
                    if (entry.getValue() != null) pending.push(entry.getValue());
                }
            } else if (value instanceof COSArray array) {
                for (var item : array) if (item != null) pending.push(item);
            }
        }
    }

    private static byte[] archive(byte[] data, boolean office) throws IOException, XMLStreamException {
        var temporary = Files.createTempFile("studhub-chat-", ".zip");
        try {
            Files.write(temporary, data);
            try (var zip = new ZipFile(temporary.toFile()); var output = new BoundedOutput();
                 var rebuilt = new ZipOutputStream(output)) {
                Set<String> names = new HashSet<>();
                int expanded = 0, rebuiltExpanded = 0, count = 0;
                boolean contentType = false, document = false, relationships = false;
                if (zip.size() == 0 || zip.size() > MAX_ENTRIES) throw limit();
                for (var entries = zip.entries(); entries.hasMoreElements();) {
                    ZipEntry entry = entries.nextElement();
                    String path = archiveName(entry.getName(), entry.isDirectory());
                    if (!names.add(path.toLowerCase(Locale.ROOT))) throw invalid();
                    if (entry.isDirectory()) continue;
                    int allowed = Math.min(MAX_BYTES, MAX_EXPANDED - expanded);
                    if (entry.getSize() < 0 || entry.getSize() > allowed) throw limit();
                    byte[] item;
                    try (var input = zip.getInputStream(entry)) {
                        item = input.readNBytes(allowed + 1);
                    }
                    if (item.length > allowed) throw limit();
                    var crc = new CRC32();
                    crc.update(item);
                    if (entry.getSize() != item.length || entry.getCrc() != crc.getValue()) throw invalid();
                    expanded += item.length;
                    count++;
                    if (office) {
                        item = officePart(path, item);
                        contentType |= path.equals("[Content_Types].xml");
                        relationships |= path.equals("_rels/.rels");
                        document |= path.equals("word/document.xml");
                    } else {
                        item = validate(path.substring(path.lastIndexOf('/') + 1), item, false).data();
                    }
                    rebuiltExpanded += item.length;
                    if (rebuiltExpanded > MAX_EXPANDED) throw limit();
                    rebuilt.putNextEntry(new ZipEntry(path));
                    rebuilt.write(item);
                    rebuilt.closeEntry();
                }
                if (count == 0 || office && !(contentType && document && relationships)) throw invalid();
                rebuilt.finish();
                return output.bytes();
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String archiveName(String raw, boolean directory) {
        String path = directory ? raw.substring(0, raw.length() - 1) : raw;
        if (path.length() > 240) throw unsafeName();
        StringJoiner result = new StringJoiner("/");
        for (String part : path.split("/", -1)) result.add(part.equals(".rels") ? part : safeName(part));
        return result.toString();
    }

    private static byte[] officePart(String path, byte[] data) throws IOException, XMLStreamException {
        if (path.matches("(?:word/media|docProps)/[\\p{L}\\p{N}_.-]+\\.(?:png|jpe?g)"))
            return image(data, extension(path).equals("png") ? "png" : "jpeg");
        if (!path.matches("\\[Content_Types\\]\\.xml|_rels/\\.rels|docProps/(?:app|core|custom)\\.xml"
            + "|word/(?:theme/|_rels/)?[a-zA-Z0-9_.-]+\\.(?:xml|rels)"
            + "|customXml/(?:item(?:Props)?[0-9]+\\.xml|_rels/item[0-9]+\\.xml\\.rels)")) throw invalid();
        if (data.length > 4 * 1024 * 1024) throw limit();
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        var xml = factory.createXMLStreamReader(new ByteArrayInputStream(data));
        try {
            int depth = 0;
            boolean mainPart = false;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) throw invalid();
                if (event == XMLStreamConstants.END_ELEMENT) depth--;
                if (event != XMLStreamConstants.START_ELEMENT) continue;
                if (++depth > 100) throw limit();
                String localName = xml.getLocalName();
                if (DOCX_BLOCKED_ELEMENTS.contains(localName)) throw invalid();
                if (depth == 1 && path.equals("word/document.xml") && (!localName.equals("document")
                    || !isWordNamespace(xml.getNamespaceURI())))
                    throw invalid();
                if (localName.equals("Relationship")) validateRelationship(xml);
                if (path.equals("[Content_Types].xml")) {
                    String type = xml.getAttributeValue(null, "ContentType");
                    if (type != null && !type.matches("application/(?:xml|vnd\\.(?:openxmlformats-(?:package|officedocument)|ms-word)\\.[a-zA-Z0-9.+-]+)|image/(?:png|jpeg)"))
                        throw invalid();
                    if (type != null && type.toLowerCase(Locale.ROOT).contains("macro")) throw invalid();
                    if ("/word/document.xml".equals(xml.getAttributeValue(null, "PartName")))
                        mainPart = (DOCX + ".main+xml").equals(type);
                }
            }
            if (path.equals("[Content_Types].xml") && !mainPart) throw invalid();
            return data;
        } finally {
            xml.close();
        }
    }

    private static boolean isWordNamespace(String namespace) {
        return "http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(namespace)
            || "http://purl.oclc.org/ooxml/wordprocessingml/main".equals(namespace);
    }

    private static void validateRelationship(XMLStreamReader xml) {
        String mode = xml.getAttributeValue(null, "TargetMode");
        String type = xml.getAttributeValue(null, "Type");
        String target = xml.getAttributeValue(null, "Target");
        if (mode != null && !mode.equals("Internal") || type == null
            || !DOCX_REL_TYPES.contains(type.substring(type.lastIndexOf('/') + 1))
            || target == null || target.contains(":") || target.contains("\\") || target.startsWith("//"))
            throw invalid();
    }

    private static ChatService.Failure invalid() {
        return new ChatService.Failure(HttpStatus.BAD_REQUEST, "FILE_CONTENT");
    }

    private static ChatService.Failure unsafeName() {
        return new ChatService.Failure(HttpStatus.BAD_REQUEST, "FILE_NAME");
    }

    private static ChatService.Failure limit() {
        return new ChatService.Failure(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_LIMIT");
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override public void write(int value) {
            if (buffer.size() >= MAX_BYTES) throw limit();
            buffer.write(value);
        }

        @Override public void write(byte[] data, int offset, int length) {
            if (length > MAX_BYTES - buffer.size()) throw limit();
            buffer.write(data, offset, length);
        }

        byte[] bytes() { return buffer.toByteArray(); }
    }
}
