let speechModule: null | {
  speak: (text: string, options?: Record<string, unknown>) => void;
  stop: () => void;
} = null;

function getSpeechModule() {
  if (speechModule) return speechModule;
  try {
    // Optional package in this workspace. If unavailable, TTS becomes no-op.
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    speechModule = require("expo-speech");
  } catch {
    speechModule = null;
  }
  return speechModule;
}

export async function localTts(text: string, language = "hi-IN"): Promise<void> {
  if (!text.trim()) return;
  const speech = getSpeechModule();
  if (!speech) return;

  speech.speak(text, {
    language,
    pitch: 1,
    rate: 0.95
  });
}

export function stopTts(): void {
  const speech = getSpeechModule();
  speech?.stop();
}
