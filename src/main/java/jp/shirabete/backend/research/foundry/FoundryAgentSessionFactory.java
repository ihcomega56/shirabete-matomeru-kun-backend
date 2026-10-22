package jp.shirabete.backend.research.foundry;

import com.azure.ai.agents.models.PromptAgentDefinition;

public interface FoundryAgentSessionFactory {
    FoundryAgentSession open(PromptAgentDefinition definition);
}
