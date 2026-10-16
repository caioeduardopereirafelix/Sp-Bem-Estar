package com.br.spbemestar.service;

import com.br.spbemestar.model.StoredPhoto;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class GridFsPhotoStorage implements PhotoStorage {

    private final GridFsTemplate gridFs;

    public GridFsPhotoStorage(GridFsTemplate gridFs) {
        this.gridFs = gridFs;
    }

    @Override
    public String store(InputStream content, String contentType) {
        ObjectId id = gridFs.store(content, UUID.randomUUID().toString(), contentType);
        return id.toHexString();
    }

    @Override
    public Optional<StoredPhoto> load(String photoId) {
        if (!ObjectId.isValid(photoId)) {
            return Optional.empty();
        }
        GridFSFile file = gridFs.findOne(byId(photoId));
        if (file == null) {
            return Optional.empty();
        }
        GridFsResource resource = gridFs.getResource(file);
        try {
            return Optional.of(new StoredPhoto(
                    photoId, resource.getContentType(), file.getLength(), resource.getInputStream()));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a foto " + photoId, e);
        }
    }

    @Override
    public void delete(String photoId) {
        if (ObjectId.isValid(photoId)) {
            gridFs.delete(byId(photoId));
        }
    }

    private static Query byId(String photoId) {
        return Query.query(Criteria.where("_id").is(new ObjectId(photoId)));
    }
}
