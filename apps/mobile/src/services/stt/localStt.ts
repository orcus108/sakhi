type SttCallbacks = {
  onResult: (text: string, isFinal: boolean) => void;
  onError: (message: string) => void;
};

let speechModule: null | {
  requestPermissionsAsync: () => Promise<{ granted: boolean }>;
  start: (options?: Record<string, unknown>) => void;
  stop: () => void;
  abort: () => void;
  addListener: (eventName: string, listener: (event: any) => void) => { remove: () => void };
} = null;

let subscriptions: Array<{ remove: () => void }> = [];

function getSpeechModule() {
  if (speechModule) return speechModule;

  try {
    // Optional native module. If missing, app falls back to manual transcript entry.
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const mod = require("expo-speech-recognition");
    speechModule = mod.ExpoSpeechRecognitionModule;
  } catch {
    speechModule = null;
  }

  return speechModule;
}

function clearListeners() {
  for (const sub of subscriptions) sub.remove();
  subscriptions = [];
}

export function isSttAvailable(): boolean {
  return !!getSpeechModule();
}

export async function startSpeechToText(callbacks: SttCallbacks): Promise<boolean> {
  const mod = getSpeechModule();
  if (!mod) {
    callbacks.onError("STT module unavailable in this build. Use keyboard mic for transcript.");
    return false;
  }

  const permission = await mod.requestPermissionsAsync();
  if (!permission.granted) {
    callbacks.onError("Microphone/speech permission denied.");
    return false;
  }

  clearListeners();

  subscriptions.push(
    mod.addListener("result", (event) => {
      const first = Array.isArray(event?.results) ? event.results[0] : null;
      const text = first?.transcript ?? event?.transcript ?? "";
      if (text) callbacks.onResult(text, !!event?.isFinal);
    })
  );

  subscriptions.push(
    mod.addListener("error", (event) => {
      callbacks.onError(event?.message ?? "Speech recognition failed.");
    })
  );

  subscriptions.push(
    mod.addListener("end", () => {
      clearListeners();
    })
  );

  mod.start({
    lang: "hi-IN",
    interimResults: true,
    continuous: false,
    maxAlternatives: 1,
    addsPunctuation: true
  });

  return true;
}

export function stopSpeechToText(): void {
  const mod = getSpeechModule();
  if (!mod) return;
  mod.stop();
}

export function cancelSpeechToText(): void {
  const mod = getSpeechModule();
  if (!mod) return;
  mod.abort();
  clearListeners();
}

export async function localStt(_audioBlobPath: string): Promise<string> {
  return "What are the danger signs for a newborn?";
}
