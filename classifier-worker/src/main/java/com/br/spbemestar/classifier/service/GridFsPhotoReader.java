package com.br.spbemestar.classifier.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Lê as fotos gravadas pela API no GridFS (bucket {@code report-photos}).
 */
@Component
@RequiredArgsConstructor
public class GridFsPhotoReader {

    private final GridFsTemplate gridFs;

    public Optional<byte[]> read(String photoId) {
        if (photoId == null || !ObjectId.isValid(photoId)) {
            return Optional.empty();
        }
        GridFSFile file = gridFs.findOne(Query.query(Criteria.where("_id").is(new ObjectId(photoId))));
        if (file == null) {
            return Optional.empty();
        }
        try (InputStream content = gridFs.getResource(file).getInputStream()) {
            return Optional.of(content.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a foto " + photoId, e);
        }
    }
}
