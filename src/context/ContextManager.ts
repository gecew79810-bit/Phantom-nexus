import { ChatMessage } from '../types/index.js';
import { config } from '../config/index.js';

export class ContextManager {
  /**
   * Estimate token count using ~4 chars per token rule of thumb
   */
  public static estimateTokens(text: string): number {
    if (!text) return 0;
    return Math.ceil(text.length / 4);
  }

  /**
   * Filter and prepare messages within token and message limits
   */
  public static prepareContext(
    currentPrompt: string,
    history: ChatMessage[] = [],
    modelContextLimit = 64000,
    maxOutputTokens = 4096
  ): ChatMessage[] {
    const budgetTokens = Math.max(1000, modelContextLimit - maxOutputTokens - 500);

    // 1. Remove duplicate adjacent messages
    const deduped: ChatMessage[] = [];
    for (const msg of history) {
      const last = deduped[deduped.length - 1];
      if (!last || last.role !== msg.role || last.content.trim() !== msg.content.trim()) {
        deduped.push(msg);
      }
    }

    // 2. Limit history to configured MAX_HISTORY_MESSAGES
    const recent = deduped.slice(-config.maxHistoryMessages);

    // 3. Assemble with budgeting
    const currentPromptTokens = this.estimateTokens(currentPrompt);
    let remainingBudget = budgetTokens - currentPromptTokens;

    const selectedHistory: ChatMessage[] = [];

    // Walk backwards from newest to oldest
    for (let i = recent.length - 1; i >= 0; i--) {
      const msg = recent[i]!;
      const msgTokens = this.estimateTokens(msg.content);

      if (msgTokens <= remainingBudget) {
        selectedHistory.unshift(msg);
        remainingBudget -= msgTokens;
      } else {
        // If history is large, create a compressed summary of skipped context if possible
        if (selectedHistory.length === 0 && remainingBudget > 200) {
          // Truncate message to fit
          const charBudget = remainingBudget * 4;
          selectedHistory.unshift({
            role: msg.role,
            content: msg.content.slice(-charBudget) + '... [truncated previous context]',
          });
        }
        break;
      }
    }

    // Always append current user prompt at the end
    selectedHistory.push({
      role: 'user',
      content: currentPrompt,
    });

    return selectedHistory;
  }
}
