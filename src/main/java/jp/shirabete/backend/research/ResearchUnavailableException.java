package jp.shirabete.backend.research;

public class ResearchUnavailableException extends RuntimeException {
    public ResearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
