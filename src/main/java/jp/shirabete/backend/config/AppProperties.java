package jp.shirabete.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.List;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotNull @Valid Research research,
        @NotNull @Valid Storage storage,
        @NotNull @Valid Search search,
        @NotNull @Valid Cors cors) {
    public enum ResearchMode { LOCAL, FOUNDRY }
    public enum StorageMode { LOCAL, AZURE_BLOB }
    public enum SearchMode { LOCAL, AZURE_AI_SEARCH }

    public record Research(@NotNull ResearchMode mode) {}
    public record Storage(@NotNull StorageMode mode, @NotNull Path directory) {}
    public record Search(@NotNull SearchMode mode) {}
    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {}
}
