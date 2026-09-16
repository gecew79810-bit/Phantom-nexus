@@
   constructor() {
     this.initializeDefaultRegistry();
   }
 
   private initializeDefaultRegistry(): void {
@@
     // TokenRa Alternate Fallback: glm-4-plus (Multimodal & agentic tool calling)
     this.registerModel({
       id: 'glm-4-plus',
       provider: 'tokenra',
       displayName: 'GLM 4 Plus (TokenRa)',
       type: 'agentic_tooling',
       capabilities: {
         coding: true,
         reasoning: true,
         longContext: true,
         vision: true,
         toolCalling: true,
         structuredOutput: true,
         streaming: true,
         fastChat: true,
       },
       contextLimit: 128000,
       outputLimit: 4096,
       costPer1kInputTokens: 0.001,
       costPer1kOutputTokens: 0.002,
       priority: 75,
       isEnabled: true,
       isPrimary: false,
       isFallback: true,
     });
+
+    // Grok (xAI) - orchestration-capable model
+    this.registerModel({
+      id: config.grokModel,
+      provider: 'grok',
+      displayName: 'Grok (xAI) Orchestrator',
+      type: 'orchestration',
+      capabilities: {
+        coding: true,
+        reasoning: true,
+        longContext: true,
+        vision: false,
+        toolCalling: true,
+        structuredOutput: true,
+        streaming: false,
+        fastChat: true,
+      },
+      contextLimit: 262144,
+      outputLimit: 8192,
+      costPer1kInputTokens: 0.0005,
+      costPer1kOutputTokens: 0.001,
+      priority: 95,
+      isEnabled: config.grokEnabled,
+      isPrimary: false,
+      isFallback: false,
+    });
@@
 }
