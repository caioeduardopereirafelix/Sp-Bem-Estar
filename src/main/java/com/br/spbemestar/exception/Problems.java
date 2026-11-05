package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
