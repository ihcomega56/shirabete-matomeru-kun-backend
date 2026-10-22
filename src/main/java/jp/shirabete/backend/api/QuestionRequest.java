package jp.shirabete.backend.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @NotBlank(message = "質問を入力してください。")
        @Size(max = 2000, message = "質問は2000文字以内で入力してください。")
        String question) {}
