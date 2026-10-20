package com.br.spbemestar.classifier.service;

import com.br.spbemestar.classifier.TestImages;
import com.br.spbemestar.classifier.config.ClassifierProperties;
import com.br.spbemestar.classifier.exception.InvalidPhotoException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.awt.Color;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Usa o modelo de teste gerado por {@code scripts/make_test_model.py}, que decide pela cor média:
 * vermelho = POTHOLE, verde = TRASH, azul = OTHER.
 */
class OnnxPhotoClassifierTest {

    private static final List<String> LABELS = List.of("POTHOLE", "TRASH", "OTHER");

    private static OnnxPhotoClassifier classifier;

    @BeforeAll
    static void loadModel() throws Exception {
        classifier = new OnnxPhotoClassifier(properties(LABELS));
    }

    @AfterAll
    static void close() throws Exception {
        classifier.destroy();
    }

    @Test
    void picksTheMostLikelyLabel() {
        assertThat(classifier.classify(TestImages.jpeg(Color.RED)).label()).isEqualTo("POTHOLE");
        assertThat(classifier.classify(TestImages.jpeg(Color.GREEN)).label()).isEqualTo("TRASH");
        assertThat(classifier.classify(TestImages.encode(TestImages.solid(Color.BLUE, 30, 90), "png")).label())
                .isEqualTo("OTHER");
    }

    @Test
    void reportsTheProbabilityOfTheChosenLabel() {
        Classification red = classifier.classify(TestImages.jpeg(Color.RED));
        // softmax de (10, 0, 0)
        assertThat(red.confidence()).isCloseTo(Math.exp(10) / (Math.exp(10) + 2), within(0.001));

        // cinza neutro: as três classes empatam
        Classification gray = classifier.classify(TestImages.jpeg(Color.GRAY));
        assertThat(gray.confidence()).isCloseTo(1 / 3.0, within(0.01));
    }

    @Test
    void rejectsBytesThatAreNotAnImage() {
        assertThatThrownBy(() -> classifier.classify("não é uma foto".getBytes()))
                .isInstanceOf(InvalidPhotoException.class);
    }

    @Test
    void failsFastWhenLabelsDoNotMatchTheModel() {
        assertThatThrownBy(() -> new OnnxPhotoClassifier(properties(List.of("POTHOLE", "TRASH"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3 saídas");
    }

    @Test
    void appliesSoftmaxOnlyWhenTheModelReturnsLogits() {
        assertThat(OnnxPhotoClassifier.toProbabilities(new float[]{0.7f, 0.2f, 0.1f}))
                .containsExactly(new double[]{0.7, 0.2, 0.1}, within(1e-6));
        assertThat(OnnxPhotoClassifier.toProbabilities(new float[]{2f, 0f, -1f}))
                .satisfies(p -> {
                    assertThat(p[0] + p[1] + p[2]).isCloseTo(1.0, within(1e-9));
                    assertThat(p[0]).isGreaterThan(p[1]).isGreaterThan(p[2]);
                });
    }

    private static ClassifierProperties properties(List<String> labels) {
        return new ClassifierProperties(new ClassPathResource("models/color-test.onnx"), labels, 224, "test",
                Duration.ofSeconds(5));
    }
}
