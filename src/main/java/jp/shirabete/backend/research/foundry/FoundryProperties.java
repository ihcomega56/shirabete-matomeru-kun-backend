package jp.shirabete.backend.research.foundry;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Validated
@ConfigurationProperties("app.foundry")
public record FoundryProperties(
        @NotNull URI projectEndpoint,
        @NotBlank String modelDeployment,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,30}") String agentNamePrefix,
        @NotNull URI learnMcpUrl,
        @NotEmpty List<String> allowedTools,
        String managedIdentityClientId,
        @Min(10) @Max(180) int timeoutSeconds,
        @Min(1) @Max(8) int maxApprovalRounds) {
    public static final String SERVER_LABEL = "microsoft-learn";
    public static final Set<String> REVIEWED_READ_ONLY_TOOLS = Set.of(
            "microsoft_docs_search", "microsoft_docs_fetch", "microsoft_code_sample_search");
    public static final URI VERIFIED_LEARN_MCP_URL = URI.create("https://learn.microsoft.com/api/mcp");

    @AssertTrue(message = "Foundry project endpoint must be an absolute HTTPS URL without credentials, query or fragment")
    public boolean isProjectEndpointSafe() {
        return projectEndpoint != null && "https".equals(projectEndpoint.getScheme())
                && projectEndpoint.getHost() != null && projectEndpoint.getUserInfo() == null
                && projectEndpoint.getQuery() == null && projectEndpoint.getFragment() == null;
    }

    @AssertTrue(message = "Only the verified https://learn.microsoft.com/api/mcp endpoint is supported")
    public boolean isLearnMcpUrlSafe() {
        return VERIFIED_LEARN_MCP_URL.equals(learnMcpUrl);
    }

    @AssertTrue(message = "MCP allowed-tools must be a subset of the reviewed read-only Microsoft Learn tools")
    public boolean isToolAllowlistSafe() {
        return allowedTools != null && !allowedTools.isEmpty()
                && REVIEWED_READ_ONLY_TOOLS.containsAll(allowedTools);
    }
}
