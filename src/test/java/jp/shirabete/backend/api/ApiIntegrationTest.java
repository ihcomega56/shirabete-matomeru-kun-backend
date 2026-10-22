package jp.shirabete.backend.api;

import jp.shirabete.backend.TestFiles;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ApiIntegrationTest {
    private static final Path DIRECTORY = TestFiles.directory("api");
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper mapper;
    private MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.directory", DIRECTORY::toString);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterAll
    static void cleanup() throws Exception {
        TestFiles.delete(DIRECTORY);
    }

    @Test
    void healthAndQuestionReportSearchRoundTrip() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        String question = "マネージドID " + UUID.randomUUID();
        String json = mvc.perform(post("/api/questions").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("question", question))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("ローカルデモ")))
                .andExpect(jsonPath("$.sources[0].title").value(org.hamcrest.Matchers.containsString("参考例")))
                .andExpect(jsonPath("$.sources[0].url").value("https://docs.spring.io/spring-boot/"))
                .andExpect(jsonPath("$.report.title").value(question))
                .andExpect(jsonPath("$.report.markdown").value(org.hamcrest.Matchers.containsString("## 出典")))
                .andExpect(jsonPath("$.report.html").value(org.hamcrest.Matchers.containsString("<h1>")))
                .andReturn().getResponse().getContentAsString();
        var response = mapper.readTree(json);
        var report = response.path("report");
        assertThat(response.size()).isEqualTo(3);
        assertThat(report.size()).isEqualTo(5);
        assertThat(Instant.parse(report.path("createdAt").asString())).isBeforeOrEqualTo(Instant.now());
        String id = report.path("id").asString();
        UUID.fromString(id);
        mvc.perform(get("/api/reports/{id}", id)).andExpect(status().isOk())
                .andExpect(content().json(report.toString()));
        mvc.perform(get("/api/reports").param("q", question)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].summary").isString())
                .andExpect(jsonPath("$[0].createdAt").isString())
                .andExpect(jsonPath("$[0].html").doesNotExist());
        mvc.perform(get("/api/reports").param("q", "該当なし-" + UUID.randomUUID()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/reports")).andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"question\":null}", "{\"question\":\"\"}",
            "{\"question\":\" \\n\\t \"}", "{\"question\":[]}", "{\"question\":{}}",
            "{\"question\":123}", "{\"question\":true}",
            "{\"question\":\"test\",\"unexpected\":true}", "{invalid"})
    void rejectsInvalidQuestions(String body) throws Exception {
        mvc.perform(post("/api/questions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void validatesLengthMalformedIdsMissingReportsAndContentType() throws Exception {
        mvc.perform(post("/api/questions").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("question", "あ".repeat(2001)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions").contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/questions").contentType(MediaType.TEXT_PLAIN).content("question"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/reports").param("q", "a".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/not-found")).andExpect(status().isNotFound());
        mvc.perform(get("/api/questions")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void escapesQuestionAndDoesNotProduceExecutableHtml() throws Exception {
        String question = "<script>alert(1)</script> ![image](https://evil.example/track) **bold**";
        String json = mvc.perform(post("/api/questions").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("question", question))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String html = mapper.readTree(json).path("report").path("html").asString();
        assertThat(html).doesNotContain("<script", "<img", "<strong>bold</strong>");
        assertThat(html).contains("&lt;script&gt;", "https://docs.spring.io/spring-boot/");
    }

    @Test
    void corsPermitsConfiguredFrontendAndRejectsUnknownOrigins() throws Exception {
        mvc.perform(options("/api/questions").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/questions").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
