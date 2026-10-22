package jp.shirabete.backend.research.foundry;

import com.azure.ai.agents.AgentsClient;
import com.azure.ai.agents.models.PromptAgentDefinition;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

import java.util.UUID;
import java.util.function.Function;

public class SdkFoundryAgentSessionFactory implements FoundryAgentSessionFactory {
    private final AgentsClient agents;
    private final Function<String, OpenAIClient> clientFactory;
    private final String namePrefix;

    public SdkFoundryAgentSessionFactory(AgentsClient agents, Function<String, OpenAIClient> clientFactory,
                                        String namePrefix) {
        this.agents = agents;
        this.clientFactory = clientFactory;
        this.namePrefix = namePrefix;
    }

    @Override
    public FoundryAgentSession open(PromptAgentDefinition definition) {
        // A unique agent per request prevents another request from changing the version/tools.
        String name = namePrefix + "-" + UUID.randomUUID().toString().replace("-", "");
        var agent = agents.createAgentVersion(name, definition);
        OpenAIClient client;
        try {
            client = clientFactory.apply(agent.getName());
        } catch (RuntimeException failure) {
            try {
                agents.deleteAgent(agent.getName());
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return new FoundryAgentSession() {
            @Override
            public Response respond(ResponseCreateParams params) {
                return client.responses().create(params);
            }

            @Override
            public void close() {
                try {
                    client.close();
                } finally {
                    agents.deleteAgent(agent.getName());
                }
            }
        };
    }
}
