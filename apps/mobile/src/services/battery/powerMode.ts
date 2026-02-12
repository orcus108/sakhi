import * as Battery from "expo-battery";
import type { PowerMode } from "../../types/domain";

export async function getPowerMode(): Promise<PowerMode> {
  const level = await Battery.getBatteryLevelAsync();
  const state = await Battery.getPowerStateAsync();
  if (state.lowPowerMode || level < 0.2) return "low_power";
  return "normal";
}
