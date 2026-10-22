package jp.shirabete.backend.research.foundry;

import com.azure.ai.agents.models.McpTool;
import com.azure.ai.agents.models.PromptAgentDefinition;
import com.azure.core.util.BinaryData;
import com.openai.models.responses.*;
import jp.shirabete.backend.research.ResearchUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FoundryResearchProviderTest {
    private static final String URL = "https://learn.microsoft.com/ja-jp/azure/active-directory/managed-identities-azure-resources/overview";
    private final FoundryAgentSessionFactory factory = mock(FoundryAgentSessionFactory.class);
    private final FoundryAgentSession session = mock(FoundryAgentSession.class);
    private final FoundryProperties properties = new FoundryProperties(
            URI.create("https://example.services.ai.azure.com/api/projects/example"), "model-deployment",
            "research", FoundryProperties.VERIFIED_LEARN_MCP_URL,
            List.of("microsoft_docs_search", "microsoft_docs_fetch"), "", 90, 2);

    private FoundryResearchProvider provider() {
        when(factory.open(any())).thenReturn(session);
        return new FoundryResearchProvider(factory, properties, JsonMapper.builder().build());
    }

    private Response response(ResponseOutputItem... items) {
        Response response = mock(Response.class);
        when(response.id()).thenReturn("response-id");
        when(response.output()).thenReturn(List.of(items));
        when(response.error()).thenReturn(Optional.empty());
        when(response.incompleteDetails()).thenReturn(Optional.empty());
        when(response.status()).thenReturn(Optional.of(ResponseStatus.COMPLETED));
        return response;
    }

    private ResponseOutputItem approval(String label, String tool, String arguments) {
        return ResponseOutputItem.ofMcpApprovalRequest(ResponseOutputItem.McpApprovalRequest.builder()
                .id("approval-id").serverLabel(label).name(tool).arguments(arguments).build());
    }

    private ResponseOutputItem result(String url) {
        return ResponseOutputItem.ofMcpCall(ResponseOutputItem.McpCall.builder()
                .id("call-id").serverLabel(FoundryProperties.SERVER_LABEL).name("microsoft_docs_search")
                .arguments("{\"query\":\"managed identity\"}").status(ResponseOutputItem.McpCall.Status.COMPLETED)
                .output("[{\"title\":\"マネージドID\",\"url\":\"" + url + "\"}]").build());
    }

    private ResponseOutputItem answer(String url) {
        return ResponseOutputItem.ofMessage(ResponseOutputMessage.builder().id("message-id")
                .status(ResponseOutputMessage.Status.COMPLETED)
                .addContent(ResponseOutputText.builder().annotations(List.of())
                        .text("{\"answer\":\"認証に利用できます [1]\", \"sources\":[{\"title\":\"マネージドID\",\"url\":\"" + url + "\"}]}").build())
                .build());
    }

    @Test
    void createsRestrictedPromptAgentApprovesOnlyReviewedToolAndReturnsGroundedSources() {
        var provider = provider();
        doReturn(
                response(approval(FoundryProperties.SERVER_LABEL, "microsoft_docs_search", "{\"query\":\"managed identity\"}")),
                response(result(URL), answer(URL))).when(session).respond(any());
        var result = provider.research("マネージドIDとは");
        assertThat(result.answer()).isEqualTo("認証に利用できます [1]");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.sources().getFirst().url()).isEqualTo(URL);
        var definition = ArgumentCaptor.forClass(PromptAgentDefinition.class);
        verify(factory).open(definition.capture());
        assertThat(definition.getValue().getModel()).isEqualTo("model-deployment");
        var tool = (McpTool) definition.getValue().getTools().getFirst();
        assertThat(tool.getServerUrl()).isEqualTo(FoundryProperties.VERIFIED_LEARN_MCP_URL.toString());
        assertThat(tool.getRequireApprovalAsMcpToolRequireApproval().getAlways().getToolNames())
                .containsExactly("microsoft_docs_search", "microsoft_docs_fetch");
        assertThat(tool.getAllowedToolsAsStringList()).containsExactly("microsoft_docs_search", "microsoft_docs_fetch");
        var serialized = JsonMapper.builder().build().readTree(BinaryData.fromObject(definition.getValue()).toString());
        assertThat(serialized.path("tools").get(0).path("require_approval").path("always")
                .path("tool_names").size()).isEqualTo(2);
        var requests = ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(session, times(2)).respond(requests.capture());
        assertThat(requests.getAllValues().get(1).previousResponseId()).contains("response-id");
        verify(session).close();
    }

    @Test
    void refusesUnknownToolsAndForeignServersWithoutSendingApprovals() {
        for (var item : List.of(
                approval(FoundryProperties.SERVER_LABEL, "delete_all", "{}"),
                approval("foreign-server", "microsoft_docs_search", "{\"query\":\"test\"}"))) {
            reset(session);
            var provider = provider();
            doReturn(response(item)).when(session).respond(any());
            assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class);
            verify(session, times(1)).respond(any());
            verify(session).close();
        }
    }

    @Test
    void refusesOffDomainFetchAndUnverifiedCitations() {
        var provider = provider();
        doReturn(response(approval(
                FoundryProperties.SERVER_LABEL, "microsoft_docs_fetch", "{\"url\":\"https://evil.example/\"}")))
                .when(session).respond(any());
        assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class);
        verify(session, times(1)).respond(any());

        reset(session);
        doReturn(response(result(URL), answer("https://learn.microsoft.com/invented"))).when(session).respond(any());
        assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class)
                .hasRootCauseMessage("Foundry returned sources not present in successful MCP output");
    }

    @Test
    void wrapsProviderFailuresAndClosesSessions() {
        var provider = provider();
        when(session.respond(any())).thenThrow(new IllegalStateException("network timeout"));
        assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class)
                .hasRootCauseMessage("network timeout");
        verify(session).close();
    }

    @Test
    void boundsApprovalRounds() {
        var provider = provider();
        doReturn(response(approval(
                FoundryProperties.SERVER_LABEL, "microsoft_docs_search", "{\"query\":\"test\"}")))
                .when(session).respond(any());
        assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class)
                .hasRootCauseMessage("MCP approval round limit exceeded");
        verify(session, times(3)).respond(any());
        verify(session).close();
    }

    @Test
    void refusesMissingToolEvidence() {
        var provider = provider();
        doReturn(response(answer(URL))).when(session).respond(any());
        assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class);
    }

    @Test
    void rejectsMalformedAnswerAndIncompleteResponse() {
        var provider = provider();
        var malformed = ResponseOutputItem.ofMessage(ResponseOutputMessage.builder().id("message-id")
                .status(ResponseOutputMessage.Status.COMPLETED).addContent(ResponseOutputText.builder()
                        .annotations(List.of()).text("not valid JSON").build()).build());
        doReturn(response(result(URL), malformed)).when(session).respond(any());
        assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class);
        var incomplete = response(result(URL), answer(URL));
        when(incomplete.status()).thenReturn(Optional.of(ResponseStatus.INCOMPLETE));
        doReturn(incomplete).when(session).respond(any());
        assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class)
                .hasRootCauseMessage("Foundry response did not complete successfully");
    }

    @Test
    void rejectsMcpLogicalErrorsEvenWhenTransportSucceeded() {
        var provider = provider();
        var error = ResponseOutputItem.ofMcpCall(ResponseOutputItem.McpCall.builder()
                .id("call-id").serverLabel(FoundryProperties.SERVER_LABEL).name("microsoft_docs_search")
                .arguments("{\"query\":\"test\"}").status(ResponseOutputItem.McpCall.Status.COMPLETED)
                .output("{\"isError\":true,\"content\":[{\"type\":\"text\",\"text\":\"" + URL + "\"}]}").build());
        doReturn(response(error, answer(URL))).when(session).respond(any());
        assertThatThrownBy(() -> provider.research("question")).isInstanceOf(ResearchUnavailableException.class)
                .hasRootCauseMessage("Microsoft Learn MCP returned an error result");
        verify(session).close();
    }
}
