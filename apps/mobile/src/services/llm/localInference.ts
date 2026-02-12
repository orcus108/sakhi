import type { DeviceProfile, LlmTask, PowerMode } from "../../types/domain";
import { getPowerMode } from "../battery/powerMode";

function getDeviceProfile(): DeviceProfile {
  return {
    ramGb: 4,
    supportsNnapi: true,
    batterySaverEnabled: false
  };
}

function pickRuntime(profile: DeviceProfile): "onnx" | "tflite" {
  return profile.supportsNnapi ? "tflite" : "onnx";
}

function pickQuant(mode: PowerMode): "int4" | "int8" {
  return mode === "low_power" ? "int4" : "int8";
}

async function runTFLite(_task: LlmTask, payload: unknown): Promise<string> {
  return JSON.stringify({ runtime: "tflite", payload });
}

async function runOnnx(_task: LlmTask, payload: unknown): Promise<string> {
  return JSON.stringify({ runtime: "onnx", payload });
}

export async function runLocalTask(task: LlmTask, payload: unknown): Promise<string> {
  const profile = getDeviceProfile();
  const mode = await getPowerMode();
  const runtime = pickRuntime(profile);
  const quant = pickQuant(mode);

  if (task === "ocr") {
    // Placeholder OCR output shape until a production OCR model is plugged in.
    return [
      "name: Unknown",
      "age: ",
      "village: ",
      "lmp: ",
      "edd: ",
      "hb: ",
      "bp: ",
      "weight: ",
      "newborn_weight: "
    ].join("\n");
  }

  const wrappedPayload = { task, quant, payload };
  if (runtime === "tflite") return runTFLite(task, wrappedPayload);
  return runOnnx(task, wrappedPayload);
}
