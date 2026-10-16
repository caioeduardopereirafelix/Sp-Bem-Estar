package com.br.spbemestar.controller;

import com.br.spbemestar.dto.CreateReportRequest;
import com.br.spbemestar.dto.ReportResponse;
import com.br.spbemestar.exception.InvalidPhotoException;
import com.br.spbemestar.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@RequestPart("photo") MultipartFile photo,
                                 @Valid @ModelAttribute CreateReportRequest request) throws IOException {
        if (photo.isEmpty()) {
            throw new InvalidPhotoException("A foto é obrigatória");
        }
        return ReportResponse.from(reportService.create(
                request.category(), request.latitude(), request.longitude(), photo.getBytes()));
    }
}
