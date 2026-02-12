export function nowMs(): number {
  return Date.now();
}

export function addDays(dateIso: string, days: number): string {
  const d = new Date(dateIso);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

export function isoDate(date = new Date()): string {
  return date.toISOString().slice(0, 10);
}
