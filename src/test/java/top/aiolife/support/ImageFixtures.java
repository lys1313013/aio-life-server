package top.aiolife.support;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ImageFixtures {
    private ImageFixtures() {}

    public static byte[] image(String format) {
        try {
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output)) {
                throw new IllegalArgumentException("Missing image writer: " + format);
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
