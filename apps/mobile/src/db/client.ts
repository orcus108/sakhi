import * as SQLite from "expo-sqlite";

export const db = SQLite.openDatabaseSync("asha_g.db");

export function exec(sql: string): void {
  db.execSync(sql);
}

export function run(sql: string, params: (string | number | null)[] = []): void {
  db.runSync(sql, params);
}

export function all<T>(sql: string, params: (string | number | null)[] = []): T[] {
  return db.getAllSync<T>(sql, params);
}
