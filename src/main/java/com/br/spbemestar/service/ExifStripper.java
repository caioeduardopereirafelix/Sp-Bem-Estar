package com.br.spbemestar.service;

import com.br.spbemestar.exception.InvalidPhotoException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;


@Component
public class ExifStripper {

    /**
     * Teto de pixels antes de decodificar. Um PNG de poucos KB pode declarar 50 000 x 50 000 pixels e
     * ocupar gigabytes de memória ao ser aberto; 40 MP cobre com folga a câmera de um celular.
     */
    static final long MAX_PIXELS = 40_000_000;

    public SanitizedPhoto strip(byte[] original) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            if (input == null) {
                throw new InvalidPhotoException("Não foi possível ler a imagem");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidPhotoException("O arquivo enviado não é uma imagem válida");
            }
            ImageReader reader = readers.next();
            try {
                PhotoFormat format = PhotoFormat.fromReaderName(reader.getFormatName());
                reader.setInput(input, true, true); // ignoreMetadata: o EXIF nem é carregado
                // as dimensões vêm do cabeçalho, sem decodificar a imagem
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width * height > MAX_PIXELS) {
                    throw new InvalidPhotoException("Imagem grande demais: %d x %d pixels (o máximo é %d megapixels)"
                            .formatted(width, height, MAX_PIXELS / 1_000_000));
                }
                BufferedImage image = reader.read(0);
                return new SanitizedPhoto(encode(image, format), format.contentType());
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidPhotoException("Imagem corrompida ou ilegível", e);
        }
    }

    private static byte[] encode(BufferedImage image, PhotoFormat format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format.writerName(), output)) {
            throw new InvalidPhotoException("Não foi possível reencodar a imagem como " + format);
        }
        return output.toByteArray();
    }

    public record SanitizedPhoto(byte[] content, String contentType) {
    }

    enum PhotoFormat {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png");

        private final String writerName;
        private final String contentType;

        PhotoFormat(String writerName, String contentType) {
            this.writerName = writerName;
            this.contentType = contentType;
        }

        static PhotoFormat fromReaderName(String readerFormatName) {
            return switch (readerFormatName.toLowerCase(Locale.ROOT)) {
                case "jpeg", "jpg" -> JPEG;
                case "png" -> PNG;
                default -> throw new InvalidPhotoException(
                        "Formato não suportado: " + readerFormatName + " (use JPEG ou PNG)");
            };
        }

        String writerName() {
            return writerName;
        }

        String contentType() {
            return contentType;
        }
    }
}
