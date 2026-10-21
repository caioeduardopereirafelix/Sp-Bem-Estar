package com.br.spbemestar.service;

import com.br.spbemestar.exception.InvalidPhotoException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

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

    @Test
    void rejectsDecompressionBombBeforeDecoding() {
        // 2,5 bilhões de pixels declarados em menos de 100 bytes: decodificar exigiria ~7 GB de memória
        byte[] bomb = pngHeader(50_000, 50_000);

        assertThatThrownBy(() -> stripper.strip(bomb))
                .isInstanceOf(InvalidPhotoException.class)
                .hasMessageContaining("grande demais");
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

    /** PNG com só o cabeçalho IHDR e um IDAT vazio: as dimensões são declaradas, não reais. */
    private static byte[] pngHeader(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0); // 8 bits, RGB
        chunk(out, "IHDR", ihdr.array());
        chunk(out, "IDAT", new byte[0]);
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }
}
