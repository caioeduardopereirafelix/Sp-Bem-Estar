package com.br.spbemestar.service;

import com.br.spbemestar.exception.InvalidPhotoException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExifStripperTest {

    private final ExifStripper stripper = new ExifStripper();

    @Test
    void removesExifSegmentFromJpeg() throws IOException {
        byte[] withExif = injectExif(image("jpg"), "GPS -23.5614 -46.6544");
        assertThat(contains(withExif, "Exif")).isTrue();

        ExifStripper.SanitizedPhoto result = stripper.strip(withExif);

        assertThat(contains(result.content(), "Exif")).isFalse();
        assertThat(contains(result.content(), "GPS")).isFalse();
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageIO.read(new ByteArrayInputStream(result.content()))).isNotNull();
    }

    @Test
    void keepsPngAsPng() throws IOException {
        ExifStripper.SanitizedPhoto result = stripper.strip(image("png"));

        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(ImageIO.read(new ByteArrayInputStream(result.content()))).isNotNull();
    }

    @Test
    void detectsFormatFromBytesNotFromClaimedType() throws IOException {
        byte[] gif = image("gif");

        assertThatThrownBy(() -> stripper.strip(gif))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("Formato não suportado");
    }

    @Test
    void rejectsNonImageContent() {
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> stripper.strip(script))
                .isInstanceOf(InvalidPhotoException.class);
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        image.setRGB(3, 3, 0xFF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /**
     * Insere um segmento APP1 (EXIF) logo após o marcador SOI do JPEG.
     */
    private static byte[] injectExif(byte[] jpeg, String payload) {
        byte[] exifHeader = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        byte[] data = payload.getBytes(StandardCharsets.ISO_8859_1);
        int segmentLength = 2 + exifHeader.length + data.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1);       // APP1
        out.write(segmentLength >> 8);
        out.write(segmentLength & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(data);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }
}
