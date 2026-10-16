package com.br.spbemestar.model;

import java.io.InputStream;

/**
 * Foto recuperada do storage. Quem recebe é responsável por fechar o {@code content}.
 */
public record StoredPhoto(String id, String contentType, long size, InputStream content) {
}
