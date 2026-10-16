package com.br.spbemestar.service;

import com.br.spbemestar.model.StoredPhoto;
import java.io.InputStream;
import java.util.Optional;


public interface PhotoStorage {


    String store(InputStream content, String contentType);

    Optional<StoredPhoto> load(String photoId);

    void delete(String photoId);
}
