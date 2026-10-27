package com.br.spbemestar.exception;

import com.br.spbemestar.config.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final TraceContext traceContext;

    @ExceptionHandler(InvalidPhotoException.class)
    ProblemDetail handleInvalidPhoto(InvalidPhotoException e) {
        return problem(HttpStatus.BAD_REQUEST, "Foto inválida", e.getMessage());
    }

    @ExceptionHandler(UnknownCategoryException.class)
    ProblemDetail handleUnknownCategory(UnknownCategoryException e) {
        return problem(HttpStatus.BAD_REQUEST, "Categoria inválida", e.getMessage());
    }

    @ExceptionHandler(InvalidBoundingBoxException.class)
    ProblemDetail handleInvalidBoundingBox(InvalidBoundingBoxException e) {
        return problem(HttpStatus.BAD_REQUEST, "Área inválida", e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Login inválido", e.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Renovação recusada", e.getMessage());
    }

    @ExceptionHandler(ReporterBlockedException.class)
    ProblemDetail handleReporterBlocked(ReporterBlockedException e) {
        return problem(HttpStatus.FORBIDDEN, "Autor bloqueado", e.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ProblemDetail handleInvalidPassword(InvalidPasswordException e) {
        return problem(HttpStatus.BAD_REQUEST, "Senha inválida", e.getMessage());
    }

    @ExceptionHandler(OperatorAlreadyExistsException.class)
    ProblemDetail handleOperatorExists(OperatorAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, "E-mail já cadastrado", e.getMessage());
    }

    @ExceptionHandler(OperatorChangeNotAllowedException.class)
    ProblemDetail handleOperatorChange(OperatorChangeNotAllowedException e) {
        return problem(HttpStatus.CONFLICT, "Alteração não permitida", e.getMessage());
    }

    @ExceptionHandler(InvalidPushSubscriptionException.class)
    ProblemDetail handleInvalidPushSubscription(InvalidPushSubscriptionException e) {
        return problem(HttpStatus.BAD_REQUEST, "Inscrição inválida", e.getMessage());
    }

    @ExceptionHandler(PushDisabledException.class)
    ProblemDetail handlePushDisabled(PushDisabledException e) {
        return problem(HttpStatus.NOT_FOUND, "Notificações desligadas", e.getMessage());
    }

    @ExceptionHandler(ReportWithoutReporterException.class)
    ProblemDetail handleReportWithoutReporter(ReportWithoutReporterException e) {
        return problem(HttpStatus.CONFLICT, "Relato sem autor", e.getMessage());
    }

    @ExceptionHandler(ReportAlreadyModeratedException.class)
    ProblemDetail handleAlreadyModerated(ReportAlreadyModeratedException e) {
        return problem(HttpStatus.CONFLICT, "Relato já moderado", e.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(InvalidResolutionException.class)
    ProblemDetail handleInvalidResolution(InvalidResolutionException e) {
        return problem(HttpStatus.BAD_REQUEST, "Resolução inválida", e.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidStatusTransitionException e) {
        return problem(HttpStatus.CONFLICT, "Transição de status inválida", e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Conflito de atualização",
                "O chamado foi alterado por outra operação. Consulte o estado atual e tente novamente.");
    }

    /**
     * Erro que ninguém previu: o stack trace vai para o log e a resposta não expõe detalhes internos,
     * só o {@code traceId} para o usuário informar e alguém achar o log certo.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Erro inesperado em {} {}", request.getMethod(), request.getRequestURI(), e);
        String traceId = traceContext.currentTraceId();
        ProblemDetail body = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", traceId == null
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
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos são inválidos");
        body.setProperty("errors", errors);
        return handleExceptionInternal(e, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
