package com.br.spbemestar.observability;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Os últimos erros inesperados (os 500), para o painel do administrador. Ficam só na memória e somem quando a API
 * reinicia: o histórico completo está nos logs, e o {@code traceId} de cada um leva até eles.
 */
@Component
public class RecentErrors {

    static final int CAPACITY = 50;
    static final int MAX_MESSAGE = 200;

    private final Deque<Entry> entries = new ArrayDeque<>(CAPACITY);
    private final Clock clock;

    public RecentErrors(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param path só o caminho, sem a query string: parâmetros podem trazer dados de quem fez a requisição
     */
    public synchronized void record(String method, String path, Throwable error, String traceId) {
        if (entries.size() == CAPACITY) {
            entries.removeLast();
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        entries.addFirst(new Entry(Instant.now(clock), method, path, error.getClass().getSimpleName(),
                message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) + "…" : message, traceId));
    }

    /** Do mais novo para o mais antigo. */
    public synchronized List<Entry> latest(int limit) {
        return entries.stream().limit(limit).toList();
    }

    public record Entry(Instant at, String method, String path, String error, String message, String traceId) {
    }
}
