package com.br.spbemestar;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

public final class TestLogin {

    private TestLogin() {
    }

    public static MvcTestResult requestCode(MockMvcTester mvc, String email) {
        return mvc.post().uri("/api/v1/auth/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)).exchange();
    }

    public static MvcTestResult verify(MockMvcTester mvc, String email, String code) {
        return mvc.post().uri("/api/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"code\": \"%s\"}".formatted(email, code)).exchange();
    }

    public static MvcTestResult login(MockMvcTester mvc, TestMailbox mailbox, String email) {
        assertThat(requestCode(mvc, email)).hasStatus(HttpStatus.ACCEPTED);
        return verify(mvc, email, mailbox.code(email.strip().toLowerCase()));
    }
}
