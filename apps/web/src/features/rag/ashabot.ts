import { searchKnowledge } from "../../db/store";
import { runLocalTask } from "../../services/llm/localInference";
import { localStt } from "../../services/stt/localStt";

function buildPrompt(query: string, context: string[]): string {
  return [
    "You are ASHABot. Provide stepwise protocol and urgent referral criteria.",
    `Question: ${query}`,
    `Context:\n${context.join("\n---\n")}`
  ].join("\n\n");
}

export async function askAshabotFromText(query: string): Promise<string> {
  const context = searchKnowledge(query);
  const prompt = buildPrompt(query, context.length ? context : ["No local context found."]);
  return runLocalTask("qa", { prompt });
}

export async function askAshabotFromVoice(blob: Blob): Promise<string> {
  const query = await localStt(blob);
  return askAshabotFromText(query);
}
