package com.br.spbemestar.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Devolve o {@code traceId} em toda resposta, inclusive 401 e 429. Quem relata um problema informa esse
 * código, e ele leva direto aos logs daquela requisição.
 */
public class TraceIdResponseFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    private final TraceContext traceContext;

    public TraceIdResponseFilter(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = traceContext.currentTraceId();
        if (traceId != null) {
            response.setHeader(HEADER, traceId);
        }
        chain.doFilter(request, response);
    }
}
