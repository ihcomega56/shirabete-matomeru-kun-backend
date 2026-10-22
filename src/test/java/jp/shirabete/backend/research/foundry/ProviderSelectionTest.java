package jp.shirabete.backend.research.foundry;

import jp.shirabete.backend.research.LocalResearchProvider;
import jp.shirabete.backend.research.ResearchProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(FoundryConfiguration.class, LocalResearchProvider.class)
            .withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder().build());

    private ApplicationContextRunner foundry() {
        return runner.withPropertyValues(
                "app.research.mode=foundry",
                "app.foundry.project-endpoint=https://example.services.ai.azure.com/api/projects/example",
                "app.foundry.model-deployment=deployed-model",
                "app.foundry.agent-name-prefix=research",
                "app.foundry.learn-mcp-url=https://learn.microsoft.com/api/mcp",
                "app.foundry.allowed-tools[0]=microsoft_docs_search",
                "app.foundry.timeout-seconds=90",
                "app.foundry.max-approval-rounds=2");
    }

    @Test
    void selectsOnlyLocalProviderWithoutCloudConfigurationOrCredential() {
        runner.withPropertyValues("app.research.mode=local").run(context -> {
            assertThat(context).hasSingleBean(ResearchProvider.class).hasSingleBean(LocalResearchProvider.class);
            assertThat(context).doesNotHaveBean(FoundryAgentSessionFactory.class);
        });
    }

    @Test
    void selectsOfficialSdkProviderWithoutMakingNetworkRequestsAtStartup() {
        foundry().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ResearchProvider.class);
            assertThat(context.getBean(ResearchProvider.class)).isInstanceOf(FoundryResearchProvider.class);
            assertThat(context.getBean(FoundryAgentSessionFactory.class)).isInstanceOf(SdkFoundryAgentSessionFactory.class);
            assertThat(context).doesNotHaveBean(LocalResearchProvider.class);
        });
    }

    @Test
    void failsClearlyWhenFoundryConfigurationIsMissing() {
        runner.withPropertyValues("app.research.mode=foundry").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsClosedForUnreviewedMcpEndpointsAndTools() {
        foundry().withPropertyValues("app.foundry.learn-mcp-url=https://evil.example/mcp")
                .run(context -> assertThat(context).hasFailed());
        foundry().withPropertyValues("app.foundry.allowed-tools[0]=delete_all")
                .run(context -> assertThat(context).hasFailed());
    }
}
