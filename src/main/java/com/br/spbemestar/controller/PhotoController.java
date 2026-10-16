package com.br.spbemestar.controller;

import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.StoredPhoto;
import com.br.spbemestar.service.PhotoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoStorage photoStorage;

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> photo(@PathVariable String id) {
        StoredPhoto photo = photoStorage.load(id).orElseThrow(() -> new ResourceNotFoundException("Foto", id));
        // A foto de um relato nunca muda: pode ficar em cache por muito tempo
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .contentLength(photo.size())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new InputStreamResource(photo.content()));
    }
}
