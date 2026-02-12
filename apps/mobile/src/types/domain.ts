export type RegisterRow = Record<string, string | number | null>;

export type DeviceProfile = {
  ramGb: number;
  supportsNnapi: boolean;
  batterySaverEnabled: boolean;
};

export type PowerMode = "normal" | "low_power";

export type LlmTask = "ocr" | "transcribe" | "qa";
