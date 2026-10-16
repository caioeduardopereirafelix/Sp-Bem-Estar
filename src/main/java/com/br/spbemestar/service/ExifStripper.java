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
