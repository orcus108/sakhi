let textExtractorModule: null | {
  isSupported?: boolean;
  extractTextFromImage?: (uri: string) => Promise<string[]>;
} = null;

function getExtractor() {
  if (textExtractorModule) return textExtractorModule;

  try {
    // Optional native module. Available when installed in a dev/client build.
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    textExtractorModule = require("expo-text-extractor");
  } catch {
    textExtractorModule = null;
  }

  return textExtractorModule;
}

export function isDeviceOcrAvailable(): boolean {
  const extractor = getExtractor();
  if (!extractor) return false;
  if (typeof extractor.isSupported === "boolean") return extractor.isSupported;
  return typeof extractor.extractTextFromImage === "function";
}

export async function extractTextFromImage(imageUri: string): Promise<string> {
  const extractor = getExtractor();
  if (!extractor || typeof extractor.extractTextFromImage !== "function") {
    throw new Error("Device OCR module unavailable in this build");
  }

  const lines = await extractor.extractTextFromImage(imageUri);
  return (lines ?? []).join("\n").trim();
}
