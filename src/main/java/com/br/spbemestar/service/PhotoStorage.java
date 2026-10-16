package com.br.spbemestar.service;

import com.br.spbemestar.model.StoredPhoto;
import java.io.InputStream;
import java.util.Optional;

/**
 * Armazenamento das fotos dos relatos. O domínio depende só desta interface:
 * trocar GridFS por S3 é uma nova implementação, sem mudar o restante do código.
 */
public interface PhotoStorage {

    /**
     * Grava a foto e devolve o identificador que deve ser salvo no relato.
     */
    String store(InputStream content, String contentType);

    Optional<StoredPhoto> load(String photoId);

    void delete(String photoId);
}
