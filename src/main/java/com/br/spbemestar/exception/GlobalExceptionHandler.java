package com.br.spbemestar.exception;

import com.br.spbemestar.config.TraceContext;
import com.br.spbemestar.observability.RecentErrors;
import com.br.spbemestar.service.mail.EmailDeliveryException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String NOT_FOUND_PAGE = loadNotFoundPage();

    private final TraceContext traceContext;
    private final RecentErrors recentErrors;

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException e) {
        ProblemDetail body = Problems.of(e.status(), e.title(), e.getMessage());
        e.properties().forEach(body::setProperty);
        return ResponseEntity.status(e.status()).headers(e.headers()).body(body);
    }

    @ExceptionHandler(EmailDeliveryException.class)
    ProblemDetail handleEmailDelivery(EmailDeliveryException e) {
        return Problems.of(HttpStatus.SERVICE_UNAVAILABLE, "E-mail não enviado",
                "Não conseguimos enviar o código agora. Tente de novo em instantes.");
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException e, HttpHeaders headers,
                                                                    HttpStatusCode status, WebRequest request) {
        if (request instanceof ServletWebRequest servlet && wantsPage(servlet.getRequest())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body(NOT_FOUND_PAGE);
        }
        return super.handleNoResourceFoundException(e, headers, status, request);
    }

    private static boolean wantsPage(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return !path.startsWith("/api/") && accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private static String loadNotFoundPage() {
        try (var page = new ClassPathResource("static/404.html").getInputStream()) {
            return new String(page.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("static/404.html não encontrado", e);
        }
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return Problems.of(HttpStatus.CONFLICT, "Conflito de atualização",
                "O chamado foi alterado por outra operação. Consulte o estado atual e tente novamente.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), e);
        String traceId = traceContext.currentTraceId();
        recentErrors.record(request.getMethod(), request.getRequestURI(), e, traceId);
        ProblemDetail body = Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", traceId == null
                ? "Algo deu errado do nosso lado. Tente de novo em instantes."
                : "Algo deu errado do nosso lado. Se continuar, informe o código " + traceId + ".");
        body.setProperty("traceId", traceId);
        return body;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail body = Problems.of(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos");
        body.setProperty("errors", errors);
        return handleExceptionInternal(e, body, headers, status, request);
    }
}
