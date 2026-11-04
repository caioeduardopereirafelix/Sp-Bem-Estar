package com.br.spbemestar.exception;

import com.br.spbemestar.controller.Open311Controller;
import com.br.spbemestar.dto.open311.Open311Error;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = Open311Controller.class)
public class Open311ExceptionHandler {

    @ExceptionHandler(Open311BadRequestException.class)
    ResponseEntity<List<Open311Error>> badRequest(Open311BadRequestException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<List<Open311Error>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, e.getName() + " inválido.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<List<Open311Error>> notFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "Chamado não encontrado.");
    }

    private static ResponseEntity<List<Open311Error>> error(HttpStatus status, String description) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .body(List.of(new Open311Error(status.value(), description)));
    }
}
