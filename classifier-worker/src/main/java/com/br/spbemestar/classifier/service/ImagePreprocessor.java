package com.br.spbemestar.classifier.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Prepara a imagem como no treino de um YOLO de classificação: redimensiona o menor lado para
 * {@code size}, recorta o centro em {@code size × size} e converte para RGB em 0..1, no layout CHW.
 */
public final class ImagePreprocessor {

    private ImagePreprocessor() {
    }

    public static float[] toChw(BufferedImage source, int size) {
        BufferedImage square = resizeAndCenterCrop(source, size);
        int plane = size * size;
        float[] chw = new float[3 * plane];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = square.getRGB(x, y);
                int i = y * size + x;
                chw[i] = ((rgb >> 16) & 0xFF) / 255f;
                chw[plane + i] = ((rgb >> 8) & 0xFF) / 255f;
                chw[2 * plane + i] = (rgb & 0xFF) / 255f;
            }
        }
        return chw;
    }

    private static BufferedImage resizeAndCenterCrop(BufferedImage source, int size) {
        double scale = (double) size / Math.min(source.getWidth(), source.getHeight());
        int width = Math.max(size, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(size, (int) Math.round(source.getHeight() * scale));

        // Desenhar em TYPE_INT_RGB também normaliza tons de cinza, paleta e canal alfa
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, -(width - size) / 2, -(height - size) / 2, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }
}
