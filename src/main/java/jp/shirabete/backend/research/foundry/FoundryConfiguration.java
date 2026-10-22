package jp.shirabete.backend.research.foundry;

import com.azure.ai.agents.AgentsClientBuilder;
import com.azure.core.http.HttpClient;
import com.azure.core.http.jdk.httpclient.JdkHttpClientBuilder;
import com.azure.core.http.policy.FixedDelayOptions;
import com.azure.core.http.policy.RetryOptions;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import jp.shirabete.backend.research.ResearchProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.research.mode", havingValue = "foundry")
@EnableConfigurationProperties(FoundryProperties.class)
public class FoundryConfiguration {
    @Bean
    FoundryAgentSessionFactory foundryAgentSessionFactory(FoundryProperties properties) {
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        HttpClient httpClient = new JdkHttpClientBuilder().connectionTimeout(Duration.ofSeconds(10))
                .readTimeout(timeout).responseTimeout(timeout).writeTimeout(timeout).build();
        var identityBuilder = new ManagedIdentityCredentialBuilder().httpClient(httpClient);
        if (properties.managedIdentityClientId() != null && !properties.managedIdentityClientId().isBlank()) {
            identityBuilder.clientId(properties.managedIdentityClientId());
        }
        var credential = identityBuilder.build();
        var agents = new AgentsClientBuilder()
                .endpoint(properties.projectEndpoint().toString())
                .credential(credential)
                .httpClient(httpClient)
                .retryOptions(new RetryOptions(new FixedDelayOptions(0, Duration.ofSeconds(1))))
                .buildAgentsClient();
        return new SdkFoundryAgentSessionFactory(agents, name -> new AgentsClientBuilder()
                .endpoint(properties.projectEndpoint().toString())
                .credential(credential)
                .httpClient(httpClient)
                .retryOptions(new RetryOptions(new FixedDelayOptions(0, Duration.ofSeconds(1))))
                .buildAgentScopedOpenAIClient(name)
                .withOptions(options -> options.timeout(timeout).maxRetries(0)), properties.agentNamePrefix());
    }

    @Bean
    ResearchProvider foundryResearchProvider(FoundryAgentSessionFactory factory, FoundryProperties properties,
                                           ObjectMapper mapper) {
        return new FoundryResearchProvider(factory, properties, mapper);
    }
}
