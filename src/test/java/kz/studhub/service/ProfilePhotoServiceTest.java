package kz.studhub.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class ProfilePhotoServiceTest {
    private final ProfilePhotoService service = new ProfilePhotoService();
    @Test void rejectsActiveContentAndOversizedUploads() {
        assertThrows(IllegalArgumentException.class,() -> service.normalize(new MockMultipartFile("photo","photo.png","image/png","<svg onload='bad()'/>".getBytes())));
        assertThrows(IllegalArgumentException.class,() -> service.normalize(new MockMultipartFile("photo",new byte[2*1024*1024+1])));
    }
    @Test void decodesAndReencodesPhotosToFixedSizePng() throws Exception {
        var input = new BufferedImage(600,400,BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(input,"jpeg",bytes);
        var normalized = service.normalize(new MockMultipartFile("photo","image.jpg","image/jpeg",bytes.toByteArray()));
        var image = ImageIO.read(new java.io.ByteArrayInputStream(normalized));
        assertEquals(256,image.getWidth()); assertEquals(256,image.getHeight());
        assertEquals(0x89,normalized[0]&255); assertEquals('P',normalized[1]);
    }
}
