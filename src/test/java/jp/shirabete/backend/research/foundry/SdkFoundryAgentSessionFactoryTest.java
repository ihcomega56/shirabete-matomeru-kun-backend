package jp.shirabete.backend.research.foundry;

import com.azure.ai.agents.AgentsClient;
import com.azure.ai.agents.models.AgentVersionDetails;
import com.azure.ai.agents.models.PromptAgentDefinition;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SdkFoundryAgentSessionFactoryTest {
    @Test
    void createsUniqueAgentsAndCleansUpClientAndAgent() {
        var agents = mock(AgentsClient.class);
        var client = mock(OpenAIClient.class);
        var details = mock(AgentVersionDetails.class);
        when(details.getName()).thenReturn("created-agent");
        when(agents.createAgentVersion(anyString(), any(PromptAgentDefinition.class))).thenReturn(details);
        var factory = new SdkFoundryAgentSessionFactory(agents, name -> client, "research");
        factory.open(new PromptAgentDefinition("model")).close();
        factory.open(new PromptAgentDefinition("model")).close();
        var names = ArgumentCaptor.forClass(String.class);
        verify(agents, times(2)).createAgentVersion(names.capture(), any(PromptAgentDefinition.class));
        assertThat(names.getAllValues()).doesNotHaveDuplicates()
                .allMatch(name -> name.matches("research-[0-9a-f]{32}"));
        verify(agents, times(2)).deleteAgent("created-agent");
        verify(client, times(2)).close();
    }

    @Test
    void deletesAgentIfAgentScopedClientCreationFails() {
        var agents = mock(AgentsClient.class);
        var details = mock(AgentVersionDetails.class);
        when(details.getName()).thenReturn("created-agent");
        when(agents.createAgentVersion(anyString(), any(PromptAgentDefinition.class))).thenReturn(details);
        var factory = new SdkFoundryAgentSessionFactory(agents, name -> {
            throw new IllegalStateException("client creation failed");
        }, "research");
        assertThatThrownBy(() -> factory.open(new PromptAgentDefinition("model")))
                .hasMessage("client creation failed");
        verify(agents).deleteAgent("created-agent");
    }
}
