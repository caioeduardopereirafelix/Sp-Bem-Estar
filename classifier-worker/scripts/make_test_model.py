"""Gera o modelo ONNX usado nos testes do worker.

Tem a mesma interface de um YOLO de classificação exportado pela Ultralytics
(entrada "images" [1, 3, 224, 224] com pixels em 0..1, saída "output0" [1, N]
com probabilidades), mas decide só pela cor média da foto:
vermelho -> POTHOLE, verde -> TRASH, azul -> OTHER.

    pip install onnx
    python classifier-worker/scripts/make_test_model.py
"""
from pathlib import Path

import onnx
from onnx import TensorProto, helper

OUTPUT = Path(__file__).resolve().parents[1] / "src/test/resources/models/color-test.onnx"
SIZE = 224
CLASSES = 3

# logits = 10 x média do canal: uma foto toda vermelha dá ~0,9999 para POTHOLE
weights = helper.make_tensor("W", TensorProto.FLOAT, [CLASSES, CLASSES],
                             [10.0 if r == c else 0.0 for r in range(CLASSES) for c in range(CLASSES)])
bias = helper.make_tensor("B", TensorProto.FLOAT, [CLASSES], [0.0] * CLASSES)

graph = helper.make_graph(
    nodes=[
        helper.make_node("GlobalAveragePool", ["images"], ["pooled"]),
        helper.make_node("Flatten", ["pooled"], ["features"], axis=1),
        helper.make_node("Gemm", ["features", "W", "B"], ["logits"], transB=1),
        helper.make_node("Softmax", ["logits"], ["output0"], axis=1),
    ],
    name="color-test",
    inputs=[helper.make_tensor_value_info("images", TensorProto.FLOAT, [1, 3, SIZE, SIZE])],
    outputs=[helper.make_tensor_value_info("output0", TensorProto.FLOAT, [1, CLASSES])],
    initializer=[weights, bias],
)

model = helper.make_model(graph, opset_imports=[helper.make_opsetid("", 13)], producer_name="spbemestar")
model.ir_version = 8  # lido por qualquer ONNX Runtime a partir da 1.10
onnx.checker.check_model(model)

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
onnx.save(model, OUTPUT)
print(f"Modelo salvo em {OUTPUT}")
