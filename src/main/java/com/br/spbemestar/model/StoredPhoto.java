package com.br.spbemestar.model;

import java.io.InputStream;


public record StoredPhoto(String id, String contentType, long size, InputStream content) {
}
