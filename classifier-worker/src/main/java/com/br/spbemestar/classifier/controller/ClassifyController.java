package com.br.spbemestar.classifier.controller;

import com.br.spbemestar.classifier.config.ClassifierProperties;
import com.br.spbemestar.classifier.dto.ClassifyResponse;
import com.br.spbemestar.classifier.exception.InvalidPhotoException;
import com.br.spbemestar.classifier.service.Classification;
import com.br.spbemestar.classifier.service.PhotoClassifier;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ClassifyController {

    static final int MAX_BYTES = 20 * 1024 * 1024;

    private final PhotoClassifier classifier;
    private final ClassifierProperties properties;
    private final MeterRegistry meters;

    @PostMapping(value = "/api/classify",
            consumes = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ClassifyResponse classify(@RequestBody byte[] photo) {
        if (photo.length == 0 || photo.length > MAX_BYTES) {
            throw new InvalidPhotoException("Foto vazia ou grande demais (" + photo.length + " bytes)");
        }
        Classification result = classifier.classify(photo);
        meters.counter("classifier.precheck", "label", result.label()).increment();
        return new ClassifyResponse(result.label(), result.confidence(), properties.modelVersion());
    }

    @ExceptionHandler(InvalidPhotoException.class)
    ProblemDetail invalidPhoto(InvalidPhotoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
