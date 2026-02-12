import { addDays, isoDate, nowMs } from "./time";
import { createVisit } from "../../db/repository";

const HBNC_DAYS = [3, 7, 14, 21, 28, 42] as const;

export function computeRisk(weightKg?: number): { riskLevel: "LOW" | "MODERATE" | "HIGH"; sncuReferralRequired: 0 | 1 } {
  if (typeof weightKg === "number" && weightKg < 1.8) {
    return { riskLevel: "HIGH", sncuReferralRequired: 1 };
  }
  if (typeof weightKg === "number" && weightKg < 2.5) {
    return { riskLevel: "MODERATE", sncuReferralRequired: 0 };
  }
  return { riskLevel: "LOW", sncuReferralRequired: 0 };
}

export async function scheduleHbncVisits(newbornCaseId: string, birthDateIso: string): Promise<void> {
  for (const day of HBNC_DAYS) {
    await createVisit({
      id: `${newbornCaseId}-${day}`,
      newbornCaseId,
      visitDay: day,
      dueDate: addDays(birthDateIso, day),
      createdAt: nowMs(),
      updatedAt: nowMs()
    });
  }
}

export { isoDate };
