import { createClaim, listClaims } from "../../db/store";

const catalog: Record<string, number> = {
  ANC: 300,
  HBNC_COMPLETION: 250,
  IMMUNIZATION_MOBILIZATION: 150
};

export function createIncentiveDraft(input: { ashaId: string; serviceType: keyof typeof catalog; relatedEntityId?: string }): void {
  const now = Date.now();
  createClaim({
    id: `${input.ashaId}-${input.serviceType}-${now}`,
    ashaId: input.ashaId,
    serviceType: input.serviceType,
    amountRupees: catalog[input.serviceType],
    status: "DRAFT",
    relatedEntityId: input.relatedEntityId,
    createdAt: now,
    updatedAt: now
  });
}

export function getLedgerSummary(ashaId: string): { total: number; pending: number; paid: number } {
  const claims = listClaims(ashaId);
  return {
    total: claims.reduce((sum, c) => sum + c.amountRupees, 0),
    pending: claims.filter((c) => c.status === "DRAFT" || c.status === "SUBMITTED").reduce((sum, c) => sum + c.amountRupees, 0),
    paid: claims.filter((c) => c.status === "PAID").reduce((sum, c) => sum + c.amountRupees, 0)
  };
}
