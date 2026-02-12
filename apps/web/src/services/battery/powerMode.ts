export type PowerMode = "normal" | "low_power";

export function getPowerMode(): PowerMode {
  if (typeof navigator !== "undefined" && "connection" in navigator) {
    const connection = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection;
    if (connection?.saveData) return "low_power";
  }
  return "normal";
}
