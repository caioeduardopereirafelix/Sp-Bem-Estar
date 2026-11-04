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

    @ExceptionHandler(InvalidPhotoException.class)
    ProblemDetail handleInvalidPhoto(InvalidPhotoException e) {
        return problem(HttpStatus.BAD_REQUEST, "Foto inválida", e.getMessage());
    }

    /** 422 com o tipo que o modelo viu, para o app oferecer trocar o tipo, trocar a foto ou enviar assim mesmo. */
    @ExceptionHandler(PhotoCategoryMismatchException.class)
    ProblemDetail handlePhotoCategoryMismatch(PhotoCategoryMismatchException e) {
        ProblemDetail body = problem(HttpStatus.UNPROCESSABLE_CONTENT, "A foto não combina com o tipo escolhido",
                e.getMessage());
        body.setProperty("chosenCategory", e.getChosenCategory());
        body.setProperty("predictedCategory", e.getPredictedCategory());
        body.setProperty("predictedCategoryName", e.getPredictedCategoryName());
        body.setProperty("confidence", e.getConfidence());
        return body;
    }

    @ExceptionHandler(UnknownCategoryException.class)
    ProblemDetail handleUnknownCategory(UnknownCategoryException e) {
        return problem(HttpStatus.BAD_REQUEST, "Categoria inválida", e.getMessage());
    }

    @ExceptionHandler(InvalidBoundingBoxException.class)
    ProblemDetail handleInvalidBoundingBox(InvalidBoundingBoxException e) {
        return problem(HttpStatus.BAD_REQUEST, "Área inválida", e.getMessage());
    }

    @ExceptionHandler(InvalidLoginCodeException.class)
    ProblemDetail handleInvalidLoginCode(InvalidLoginCodeException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Código inválido", e.getMessage());
    }

    @ExceptionHandler(LoginCodeThrottledException.class)
    ResponseEntity<ProblemDetail> handleLoginCodeThrottled(LoginCodeThrottledException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(problem(HttpStatus.TOO_MANY_REQUESTS, "Espere para pedir outro código", e.getMessage()));
    }

    @ExceptionHandler(EmailDeliveryException.class)
    ProblemDetail handleEmailDelivery(EmailDeliveryException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "E-mail não enviado",
                "Não conseguimos enviar o código agora. Tente de novo em instantes.");
    }

    @ExceptionHandler(StaffAccessDisabledException.class)
    ProblemDetail handleStaffAccessDisabled(StaffAccessDisabledException e) {
        return problem(HttpStatus.FORBIDDEN, "Acesso desativado", e.getMessage());
    }

    /** O app reconhece o {@code loginRequired} e abre a tela de entrar, em vez de só mostrar o erro. */
    @ExceptionHandler(CitizenLoginRequiredException.class)
    ProblemDetail handleCitizenLoginRequired(CitizenLoginRequiredException e) {
        ProblemDetail body = problem(HttpStatus.FORBIDDEN, "Entre para relatar", e.getMessage());
        body.setProperty("loginRequired", true);
        return body;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Renovação recusada", e.getMessage());
    }

    @ExceptionHandler(ReporterBlockedException.class)
    ProblemDetail handleReporterBlocked(ReporterBlockedException e) {
        return problem(HttpStatus.FORBIDDEN, "Autor bloqueado", e.getMessage());
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

    /**
     * Endereço que não existe. Quem abriu no navegador vê a página "não encontrada", com o caminho de volta ao mapa;
     * a API e quem pede JSON continuam recebendo o ProblemDetail.
     */
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
        recentErrors.record(request.getMethod(), request.getRequestURI(), e, traceId); // para o painel do administrador
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
