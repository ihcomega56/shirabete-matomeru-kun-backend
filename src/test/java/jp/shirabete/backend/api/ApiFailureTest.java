package jp.shirabete.backend.api;

import jp.shirabete.backend.research.ResearchUnavailableException;
import jp.shirabete.backend.repository.StorageException;
import jp.shirabete.backend.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiFailureTest {
    @Test
    void mapsProviderAndStorageFailuresWithoutLeakingInternals() throws Exception {
        var service = mock(QuestionService.class);
        when(service.ask("provider")).thenThrow(new ResearchUnavailableException("secret-endpoint", null));
        when(service.ask("storage")).thenThrow(new StorageException("private-file-path", null));
        var mvc = MockMvcBuilders.standaloneSetup(new ApiController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/api/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"provider\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.status").value(502))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-endpoint"))));
        mvc.perform(post("/api/questions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"storage\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-file-path"))));
    }
}
