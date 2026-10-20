package com.br.spbemestar.classifier.service;

import com.br.spbemestar.classifier.TestImages;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ImagePreprocessorTest {

    private static final int SIZE = 32;
    private static final int PLANE = SIZE * SIZE;

    @Test
    void convertsToChannelsFirstInZeroToOneRange() {
        float[] chw = ImagePreprocessor.toChw(TestImages.solid(new Color(255, 128, 0), 50, 50), SIZE);

        assertThat(chw).hasSize(3 * PLANE);
        assertThat(mean(chw, 0)).isCloseTo(1.0, within(0.01));
        assertThat(mean(chw, 1)).isCloseTo(128 / 255.0, within(0.01));
        assertThat(mean(chw, 2)).isCloseTo(0.0, within(0.01));
    }

    @Test
    void cropsTheCenterOfWideImages() {
        // 300x100: quadrado vermelho no meio, faixas azuis nas laterais que o recorte deve descartar
        BufferedImage wide = TestImages.solid(Color.BLUE, 300, 100);
        Graphics2D g = wide.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(100, 0, 100, 100);
        g.dispose();

        float[] chw = ImagePreprocessor.toChw(wide, SIZE);

        assertThat(mean(chw, 0)).isGreaterThan(0.95);
        assertThat(mean(chw, 2)).isLessThan(0.05);
    }

    @Test
    void handlesImagesWithAlphaAndGrayscale() {
        BufferedImage gray = new BufferedImage(40, 60, BufferedImage.TYPE_BYTE_GRAY);
        BufferedImage alpha = new BufferedImage(40, 60, BufferedImage.TYPE_INT_ARGB);

        assertThat(ImagePreprocessor.toChw(gray, SIZE)).hasSize(3 * PLANE);
        assertThat(ImagePreprocessor.toChw(alpha, SIZE)).hasSize(3 * PLANE);
    }

    private static double mean(float[] chw, int channel) {
        double sum = 0;
        for (int i = channel * PLANE; i < (channel + 1) * PLANE; i++) {
            sum += chw[i];
        }
        return sum / PLANE;
    }
}
