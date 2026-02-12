import { createVisit } from "../../db/store";

const DAYS = [3, 7, 14, 21, 28, 42] as const;

function addDays(dateIso: string, days: number): string {
  const d = new Date(dateIso);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function computeRisk(weightKg?: number): { riskLevel: "LOW" | "MODERATE" | "HIGH"; sncuReferral: boolean } {
  if (typeof weightKg === "number" && weightKg < 1.8) return { riskLevel: "HIGH", sncuReferral: true };
  if (typeof weightKg === "number" && weightKg < 2.5) return { riskLevel: "MODERATE", sncuReferral: false };
  return { riskLevel: "LOW", sncuReferral: false };
}

export function scheduleHbnc(newbornCaseId: string, birthDateIso: string): void {
  const now = Date.now();
  for (const day of DAYS) {
    createVisit({
      id: `${newbornCaseId}-${day}`,
      newbornCaseId,
      visitDay: day,
      dueDate: addDays(birthDateIso, day),
      createdAt: now,
      updatedAt: now
    });
  }
}
