package com.br.spbemestar.classifier.service;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.br.spbemestar.classifier.config.ClassifierProperties;
import com.br.spbemestar.classifier.exception.InvalidPhotoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Classifica a foto com um modelo ONNX, como o exportado por {@code yolo export format=onnx} a partir
 * de um YOLO de classificação. A sessão do ONNX Runtime é thread-safe e fica aberta durante toda a
 * vida do worker.
 */
@Slf4j
@Service
public class OnnxPhotoClassifier implements PhotoClassifier, DisposableBean {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;
    private final int inputSize;
    private final List<String> labels;

    public OnnxPhotoClassifier(ClassifierProperties properties) throws OrtException {
        Objects.requireNonNull(properties.model(), "classifier.model não configurado");
        if (properties.labels() == null || properties.labels().isEmpty()) {
            throw new IllegalStateException("classifier.labels não configurado");
        }
        this.labels = List.copyOf(properties.labels());
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(read(properties), new OrtSession.SessionOptions());
        this.inputName = session.getInputNames().iterator().next();
        this.inputSize = resolveInputSize(session.getInputInfo().get(inputName), properties.inputSize());

        long outputs = outputCount(session);
        if (outputs > 0 && outputs != labels.size()) {
            throw new IllegalStateException("O modelo tem %d saídas, mas classifier.labels tem %d rótulos"
                    .formatted(outputs, labels.size()));
        }
        log.info("Modelo {} carregado: entrada {}x{}, rótulos {}",
                properties.model().getDescription(), inputSize, inputSize, labels);
    }

    @Override
    public Classification classify(byte[] photo) {
        float[] pixels = ImagePreprocessor.toChw(decode(photo), inputSize);
        long[] shape = {1, 3, inputSize, inputSize};

        try (OnnxTensor input = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), shape);
             OrtSession.Result result = session.run(Map.of(inputName, input))) {
            float[] scores = ((float[][]) result.get(0).getValue())[0];
            if (scores.length != labels.size()) {
                throw new IllegalStateException("O modelo devolveu %d valores para %d rótulos"
                        .formatted(scores.length, labels.size()));
            }
            double[] probabilities = toProbabilities(scores);
            int best = 0;
            for (int i = 1; i < probabilities.length; i++) {
                if (probabilities[i] > probabilities[best]) {
                    best = i;
                }
            }
            return new Classification(labels.get(best), probabilities[best]);
        } catch (OrtException e) {
            throw new IllegalStateException("Falha na inferência do modelo", e);
        }
    }

    @Override
    public void destroy() throws OrtException {
        session.close();
    }

    // O YOLO de classificação já exporta a saída com softmax; outros modelos podem devolver logits
    static double[] toProbabilities(float[] scores) {
        double sum = 0;
        boolean inRange = true;
        for (float s : scores) {
            sum += s;
            inRange &= s >= 0 && s <= 1;
        }
        double[] result = new double[scores.length];
        if (inRange && Math.abs(sum - 1) < 1e-3) {
            for (int i = 0; i < scores.length; i++) {
                result[i] = scores[i];
            }
            return result;
        }
        double max = Double.NEGATIVE_INFINITY;
        for (float s : scores) {
            max = Math.max(max, s);
        }
        double total = 0;
        for (int i = 0; i < scores.length; i++) {
            result[i] = Math.exp(scores[i] - max);
            total += result[i];
        }
        for (int i = 0; i < scores.length; i++) {
            result[i] /= total;
        }
        return result;
    }

    private static BufferedImage decode(byte[] photo) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(photo));
            if (image == null) {
                throw new InvalidPhotoException("Formato de imagem não reconhecido");
            }
            return image;
        } catch (IOException e) {
            throw new InvalidPhotoException("Imagem corrompida: " + e.getMessage());
        }
    }

    private static byte[] read(ClassifierProperties properties) {
        try {
            return properties.model().getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o modelo " + properties.model().getDescription(), e);
        }
    }

    // Entrada esperada: [1, 3, altura, largura]; -1 indica dimensão dinâmica
    private static int resolveInputSize(NodeInfo input, int fallback) {
        long[] shape = ((TensorInfo) input.getInfo()).getShape();
        if (shape.length != 4 || shape[1] != 3) {
            throw new IllegalStateException("Entrada do modelo deveria ser [1, 3, H, W], mas é "
                    + Arrays.toString(shape));
        }
        return shape[3] > 0 ? (int) shape[3] : fallback;
    }

    private static long outputCount(OrtSession session) throws OrtException {
        NodeInfo output = session.getOutputInfo().values().iterator().next();
        long[] shape = ((TensorInfo) output.getInfo()).getShape();
        return shape[shape.length - 1];
    }
}
