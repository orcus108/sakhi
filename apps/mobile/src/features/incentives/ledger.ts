import { createClaim, listClaims } from "../../db/repository";

const incentiveCatalog: Record<string, number> = {
  ANC: 300,
  HBNC_COMPLETION: 250,
  IMMUNIZATION_MOBILIZATION: 150
};

export async function submitIncentiveDraft(input: {
  ashaId: string;
  serviceType: keyof typeof incentiveCatalog;
  relatedEntityId?: string;
}): Promise<void> {
  const now = Date.now();
  await createClaim({
    id: `${input.ashaId}-${input.serviceType}-${now}`,
    ashaId: input.ashaId,
    serviceType: input.serviceType,
    amountRupees: incentiveCatalog[input.serviceType],
    status: "DRAFT",
    relatedEntityId: input.relatedEntityId,
    createdAt: now,
    updatedAt: now
  });
}

export async function getLedgerSummary(ashaId: string): Promise<{
  totalPotential: number;
  approved: number;
  paid: number;
  pending: number;
}> {
  const claims = await listClaims(ashaId);
  return {
    totalPotential: claims.reduce((a, c) => a + c.amountRupees, 0),
    approved: claims.filter((c) => c.status === "APPROVED").reduce((a, c) => a + c.amountRupees, 0),
    paid: claims.filter((c) => c.status === "PAID").reduce((a, c) => a + c.amountRupees, 0),
    pending: claims.filter((c) => c.status === "SUBMITTED" || c.status === "DRAFT").reduce((a, c) => a + c.amountRupees, 0)
  };
}
