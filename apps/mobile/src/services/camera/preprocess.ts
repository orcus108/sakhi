export type PreprocessResult = {
  enhancedImagePath: string;
  confidenceHint: number;
};

export async function preprocessImage(imagePath: string): Promise<PreprocessResult> {
  return {
    enhancedImagePath: imagePath,
    confidenceHint: 0.88
  };
}
