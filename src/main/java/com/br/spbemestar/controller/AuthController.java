package com.br.spbemestar.controller;

import com.br.spbemestar.dto.LoginCodeSentResponse;
import com.br.spbemestar.dto.LoginResponse;
import com.br.spbemestar.dto.RequestLoginCodeRequest;
import com.br.spbemestar.dto.VerifyLoginCodeRequest;
import com.br.spbemestar.service.LoginCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Login sem senha, o mesmo para cidadão, equipe da prefeitura e CEO: código de 6 números enviado por e-mail. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginCodeService loginCodes;

    @PostMapping("/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LoginCodeSentResponse requestCode(@Valid @RequestBody RequestLoginCodeRequest request) {
        return loginCodes.requestCode(request.email());
    }

    /** O {@code role} da resposta diz quem entrou: {@code CITIZEN}, {@code OPERATOR}, {@code ADMIN} ou {@code CEO}. */
    @PostMapping("/verify")
    public LoginResponse verify(@Valid @RequestBody VerifyLoginCodeRequest request) {
        return loginCodes.verify(request.email(), request.code(), request.reporterToken());
    }
}
