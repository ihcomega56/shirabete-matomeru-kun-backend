package jp.shirabete.backend.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jp.shirabete.backend.domain.Report;
import jp.shirabete.backend.domain.ReportSummary;
import jp.shirabete.backend.service.QuestionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final QuestionService service;

    public ApiController(QuestionService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @PostMapping("/questions")
    public QuestionResponse ask(@Valid @RequestBody QuestionRequest request) {
        return service.ask(request.question());
    }

    @GetMapping("/reports")
    public List<ReportSummary> reports(@RequestParam(defaultValue = "") @Size(max = 200) String q) {
        return service.search(q);
    }

    @GetMapping("/reports/{id}")
    public Report report(@PathVariable UUID id) {
        return service.get(id);
    }
}
