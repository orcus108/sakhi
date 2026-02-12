declare module "expo-text-extractor" {
  export const isSupported: boolean;
  export function extractTextFromImage(uri: string): Promise<string[]>;
}

declare module "expo-speech-recognition" {
  export const ExpoSpeechRecognitionModule: {
    requestPermissionsAsync: () => Promise<{ granted: boolean }>;
    start: (options?: Record<string, unknown>) => void;
    stop: () => void;
    abort: () => void;
    addListener: (eventName: string, listener: (event: any) => void) => { remove: () => void };
  };
}
