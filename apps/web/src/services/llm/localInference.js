import { getPowerMode } from "../battery/powerMode";
export async function runLocalTask(task, payload) {
    const mode = getPowerMode();
    const quant = mode === "low_power" ? "int4" : "int8";
    const runtime = "webgpu" in navigator ? "onnx-web" : "tflite-web";
    return JSON.stringify({ runtime, quant, task, payload });
}
