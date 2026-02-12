import { createVisit } from "../../db/store";
const DAYS = [3, 7, 14, 21, 28, 42];
function addDays(dateIso, days) {
    const d = new Date(dateIso);
    d.setDate(d.getDate() + days);
    return d.toISOString().slice(0, 10);
}
export function computeRisk(weightKg) {
    if (typeof weightKg === "number" && weightKg < 1.8)
        return { riskLevel: "HIGH", sncuReferral: true };
    if (typeof weightKg === "number" && weightKg < 2.5)
        return { riskLevel: "MODERATE", sncuReferral: false };
    return { riskLevel: "LOW", sncuReferral: false };
}
export function scheduleHbnc(newbornCaseId, birthDateIso) {
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
