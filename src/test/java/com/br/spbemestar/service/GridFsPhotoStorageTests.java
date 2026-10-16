package com.br.spbemestar.service;

import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.model.StoredPhoto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GridFsPhotoStorageTests {

    @Autowired
    PhotoStorage storage;

    @Test
    void storesAndLoadsPhoto() throws IOException {
        byte[] content = "conteudo-da-foto".getBytes(StandardCharsets.UTF_8);

        String id = storage.store(new ByteArrayInputStream(content), "image/jpeg");

        StoredPhoto photo = storage.load(id).orElseThrow();
        try (InputStream in = photo.content()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        assertThat(photo.size()).isEqualTo(content.length);
    }

    @Test
    void deletesPhoto() {
        String id = storage.store(new ByteArrayInputStream(new byte[] {1, 2, 3}), "image/png");

        storage.delete(id);

        assertThat(storage.load(id)).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownOrInvalidId() {
        assertThat(storage.load("000000000000000000000000")).isEmpty();
        assertThat(storage.load("nao-e-um-object-id")).isEmpty();
    }
}
