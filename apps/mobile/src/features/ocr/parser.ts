import type { RegisterRow } from "../../types/domain";

const EXPECTED_COLUMNS = [
  "name",
  "age",
  "village",
  "lmp",
  "edd",
  "hb",
  "bp",
  "weight",
  "newborn_weight"
];

export function parseRegisterText(text: string): RegisterRow {
  const row: RegisterRow = {};
  const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);

  for (const column of EXPECTED_COLUMNS) row[column] = null;

  for (const line of lines) {
    const [k, ...rest] = line.split(":");
    if (!k || rest.length === 0) continue;
    const key = k.trim().toLowerCase().replace(/\s+/g, "_");
    const value = rest.join(":").trim();
    row[key] = value;
  }

  return row;
}
