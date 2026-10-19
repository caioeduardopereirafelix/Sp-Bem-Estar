# Treino do classificador

[![Abrir no Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/caioeduardopereirafelix/Sp-Bem-Estar/blob/main/classifier-worker/training/train_classifier.ipynb)

O notebook [`train_classifier.ipynb`](train_classifier.ipynb) treina o modelo que o worker usa para conferir se a foto de um relato mostra o problema informado. Ele roda no Google Colab, com GPU gratuita, e entrega o `classifier.onnx` pronto para o worker.

## Passo a passo

1. Clique em **Abrir no Colab**, acima.
2. `Ambiente de execução → Alterar o tipo de ambiente de execução → GPU T4`.
3. Prepare as fotos (veja abaixo) e, se for usar o Roboflow, guarde a chave nos **Segredos** do Colab como `ROBOFLOW_API_KEY`.
4. `Ambiente de execução → Executar tudo`. O treino leva de 20 a 60 minutos.
5. No fim, o Colab baixa o `classifier.onnx` e o `metrics.json`. Coloque o modelo em `classifier-worker/models/classifier.onnx`.

## Fotos

O notebook junta duas fontes, nas células da seção 3:

- **Suas fotos (recomendado):** um `fotos.zip` no Google Drive, em `MyDrive/sp-bem-estar/fotos.zip`, com uma pasta por categoria. Os nomes podem ser o código (`POTHOLE`) ou em português (`buraco`, `lixo`, `arvore`, `alagamento`, `esgoto`, `bueiro`, `calcada`, `poste`, `ponto_de_onibus`, `mato`, `outro`).
- **Roboflow Universe:** projetos públicos listados em `ROBOFLOW_SOURCES`. Em projetos de detecção, toda imagem com marcação vira exemplo da categoria.

A pasta `outro/` (`OTHER`) é obrigatória: fotos de rua **sem** problema nenhum, para o modelo aprender a dizer "isso não é um buraco".

Com 150 a 300 fotos por categoria já sai um resultado razoável, porque o treino parte de um modelo pré-treinado. Categorias com menos de 20 fotos ficam fora do treino; os relatos delas vão para moderação até o modelo ser treinado de novo com mais fotos.

## O que o notebook confere

- acurácia geral e por categoria, as confusões mais comuns e as fotos que o modelo errou;
- que o ONNX, rodado com o **mesmo pré-processamento do worker Java**, dá a mesma resposta que o modelo do treino;
- que os nomes das categorias foram gravados no modelo (metadado `names`), de onde o worker os lê.

## Testar o notebook sem GPU

Com `SMOKE_TEST=1`, o notebook usa fotos sintéticas e treina 2 épocas na CPU, em poucos minutos. Serve para validar mudanças no próprio notebook:

```bash
pip install ultralytics onnx onnxruntime onnxslim nbconvert ipykernel
SMOKE_TEST=1 WORK_DIR=/tmp/sp-treino jupyter nbconvert --to notebook --execute \
    --output /tmp/sp-treino/executado.ipynb classifier-worker/training/train_classifier.ipynb
```
