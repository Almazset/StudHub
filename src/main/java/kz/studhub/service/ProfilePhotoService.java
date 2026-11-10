package kz.studhub.service;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfilePhotoService {
    public byte[] normalize(MultipartFile upload) {
        if (upload.isEmpty() || upload.getSize() > 2 * 1024 * 1024) throw new IllegalArgumentException("settings.error.photoSize");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("settings.error.photoFormat");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg"))) throw new IllegalArgumentException("settings.error.photoFormat");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16_000_000) throw new IllegalArgumentException("settings.error.photoSize");
                BufferedImage image = reader.read(0);
                BufferedImage result = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                var graphics = result.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int side = Math.min(width, height), x = (width - side) / 2, y = (height - side) / 2;
                graphics.drawImage(image, 0, 0, 256, 256, x, y, x + side, y + side, null); graphics.dispose();
                var output = new ByteArrayOutputStream(); ImageIO.write(result, "png", output); return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new IllegalArgumentException("settings.error.photoFormat"); }
    }
}
