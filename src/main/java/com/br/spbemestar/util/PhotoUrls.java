package com.br.spbemestar.util;

public final class PhotoUrls {

    private static final String PATH = "/api/v1/photos/";

    private PhotoUrls() {
    }

    public static String path(String photoId) {
        return photoId == null ? null : PATH + photoId;
    }

    public static String absolute(String baseUrl, String photoId) {
        return photoId == null ? null : baseUrl + PATH + photoId;
    }
}
