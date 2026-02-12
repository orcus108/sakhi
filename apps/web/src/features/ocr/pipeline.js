import { createDigitizedRecord } from "../../db/store";
import { runLocalTask } from "../../services/llm/localInference";
function parseKV(text) {
    const result = {
        name: null,
        village: null,
        hb: null,
        bp: null,
        newborn_weight: null
    };
    for (const line of text.split("\n")) {
        const [k, v] = line.split(":");
        if (!k || !v)
            continue;
        result[k.trim().toLowerCase().replace(/\s+/g, "_")] = v.trim();
    }
    return result;
}
export async function digitizeTextToRecord(rawText) {
    const ocrOut = await runLocalTask("ocr", { rawText });
    const mergedText = `${rawText}\nmodel_output: ${ocrOut}`;
    const record = parseKV(mergedText);
    createDigitizedRecord(record);
    return record;
}
