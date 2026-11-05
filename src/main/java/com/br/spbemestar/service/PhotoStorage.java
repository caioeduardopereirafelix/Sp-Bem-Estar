package com.br.spbemestar.service;

import com.br.spbemestar.model.StoredPhoto;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

public interface PhotoStorage {

    String store(InputStream content, String contentType);

    default String store(ExifStripper.SanitizedPhoto photo) {
        return store(new ByteArrayInputStream(photo.content()), photo.contentType());
    }

    Optional<StoredPhoto> load(String photoId);

    void delete(String photoId);
}
