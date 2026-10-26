package com.br.spbemestar.dto;

/** @param publicKey chave VAPID para o {@code pushManager.subscribe()}; nula quando o push está desligado */
public record PushConfigResponse(boolean enabled, String publicKey) {
}
