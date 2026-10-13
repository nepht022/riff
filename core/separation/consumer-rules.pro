# O ONNX Runtime chama as classes Java pelo JNI: o R8 não pode renomeá-las nem tirá-las.
-keep class ai.onnxruntime.** { *; }
