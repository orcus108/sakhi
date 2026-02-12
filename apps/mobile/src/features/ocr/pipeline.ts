import { preprocessImage } from "../../services/camera/preprocess";
import { runLocalTask } from "../../services/llm/localInference";
import { extractTextFromImage } from "../../services/ocr/deviceOcr";
import { parseRegisterText } from "./parser";
import { upsertDigitizedRecord } from "../../db/repository";

function normalizeDialect(text: string): string {
  return text
    .replace(/baccha/gi, "baby")
    .replace(/vajan/gi, "weight")
    .replace(/bukhar/gi, "fever")
    .replace(/gaon/gi, "village");
}

function toCsvRow(record: Record<string, string | number | null>): string {
  const keys = Object.keys(record);
  const values = keys.map((k) => `"${String(record[k] ?? "").replace(/"/g, '""')}"`);
  return `${keys.join(",")}\n${values.join(",")}`;
}

export async function textToStructuredData(rawText: string): Promise<{
  json: Record<string, string | number | null>;
  csv: string;
}> {
  const normalized = normalizeDialect(rawText);
  const structured = parseRegisterText(normalized);
  await upsertDigitizedRecord(structured);
  return {
    json: structured,
    csv: toCsvRow(structured)
  };
}

export async function cameraToStructuredData(imagePath: string): Promise<{
  json: Record<string, string | number | null>;
  csv: string;
  ocrText: string;
}> {
  const preprocessed = await preprocessImage(imagePath);

  let ocrRaw = "";
  try {
    ocrRaw = await extractTextFromImage(preprocessed.enhancedImagePath);
  } catch {
    // Fallback to bundled local inference stub when OCR native module is unavailable.
    ocrRaw = await runLocalTask("ocr", { imagePath: preprocessed.enhancedImagePath });
  }

  const normalized = normalizeDialect(ocrRaw);
  const structured = parseRegisterText(normalized);
  await upsertDigitizedRecord(structured);
  return {
    json: structured,
    csv: toCsvRow(structured),
    ocrText: normalized
  };
}
