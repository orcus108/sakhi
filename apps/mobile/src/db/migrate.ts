import { exec } from "./client";
import schemaSql from "./schema";

let migrated = false;

export function migrateDb(): void {
  if (migrated) return;
  exec(schemaSql);
  migrated = true;
}
