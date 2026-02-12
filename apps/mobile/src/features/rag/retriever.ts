import { searchKnowledge } from "../../db/repository";

export async function retrievePassages(query: string): Promise<string[]> {
  const rows = await searchKnowledge(query);
  if (rows.length > 0) return rows.map((r) => r.content);

  return [
    "Danger signs include jaundice in first 24h, breathing rate > 60/min, chest indrawing, poor feeding, convulsions, hypothermia.",
    "Immediate referral to SNCU is required for convulsions, severe breathing distress, or birth weight below 1.8 kg."
  ];
}
