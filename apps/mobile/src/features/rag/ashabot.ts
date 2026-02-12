import { localStt } from "../../services/stt/localStt";
import { retrievePassages } from "./retriever";

function protocolFromQuery(query: string): string {
  const q = query.toLowerCase();

  if (q.includes("danger") || q.includes("newborn") || q.includes("jaundice") || q.includes("convulsion")) {
    return [
      "Danger signs for newborn:",
      "1. Breathing rate > 60/min or chest indrawing.",
      "2. Convulsions or poor feeding.",
      "3. Fever, hypothermia, or jaundice in first 24 hours.",
      "Immediate referral: any convulsion, severe breathing distress, or birth weight < 1.8 kg (SNCU)."
    ].join("\n");
  }

  if (q.includes("hbnc") || q.includes("visit") || q.includes("schedule")) {
    return [
      "HBNC schedule:",
      "Day 3, 7, 14, 21, 28, 42.",
      "At each visit check temperature, breathing, feeding, weight, and danger signs.",
      "Refer immediately if danger signs are present."
    ].join("\n");
  }

  return [
    "Clinical guidance:",
    "Assess for danger signs, counsel family, and refer severe cases immediately.",
    "Use HBNC visit protocol and document findings in the register."
  ].join("\n");
}

export async function askAshabotFromText(query: string): Promise<string> {
  const trimmed = query.trim();
  if (!trimmed) return "Please enter a question.";

  const passages = await retrievePassages(trimmed);
  const protocol = protocolFromQuery(trimmed);

  return `${protocol}\n\nReference:\n${passages.slice(0, 2).join("\n")}`;
}

export async function askAshabot(audioPath: string): Promise<string> {
  const query = await localStt(audioPath);
  return askAshabotFromText(query);
}
