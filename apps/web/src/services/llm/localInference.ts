import { getPowerMode } from "../battery/powerMode";

type LlmTask = "ocr" | "qa";

export async function runLocalTask(task: LlmTask, payload: unknown): Promise<string> {
  const mode = getPowerMode();
  const quant = mode === "low_power" ? "int4" : "int8";
  const runtime = "webgpu" in navigator ? "onnx-web" : "tflite-web";

  return JSON.stringify({ runtime, quant, task, payload });
}
