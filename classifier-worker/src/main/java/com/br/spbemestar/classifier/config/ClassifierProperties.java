package com.br.spbemestar.classifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.time.Duration;
import java.util.List;

/**
 * @param model         arquivo ONNX do modelo (ex.: {@code file:models/classifier.onnx})
 * @param labels        rótulos na mesma ordem das saídas do modelo
 * @param inputSize     lado da imagem de entrada, usado só quando o modelo não declara um tamanho fixo
 * @param modelVersion  versão do modelo, enviada junto com cada resultado
 * @param confirmTimeout espera máxima pelo ack do broker ao publicar o resultado
 */
@ConfigurationProperties("classifier")
public record ClassifierProperties(
        Resource model,
        List<String> labels,
        @DefaultValue("224") int inputSize,
        @DefaultValue("dev") String modelVersion,
        @DefaultValue("5s") Duration confirmTimeout
) {
}
