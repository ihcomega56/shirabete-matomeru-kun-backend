package jp.shirabete.backend.api;

import jp.shirabete.backend.research.ResearchUnavailableException;
import jp.shirabete.backend.repository.StorageException;
import jp.shirabete.backend.service.ReportNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ReportNotFoundException.class)
    ProblemDetail notFound(ReportNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    ProblemDetail storageFailure(StorageException exception) {
        LOG.error("Report storage operation failed", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "レポートの保存・検索を利用できません。");
    }

    @ExceptionHandler(ResearchUnavailableException.class)
    ProblemDetail researchFailure(ResearchUnavailableException exception) {
        LOG.error("Research provider unavailable", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "調査プロバイダーを利用できません。");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedFailure(Exception exception) {
        LOG.error("Unexpected API failure", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "処理中にエラーが発生しました。");
    }
}
