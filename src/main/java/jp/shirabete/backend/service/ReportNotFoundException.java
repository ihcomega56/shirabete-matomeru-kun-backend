package jp.shirabete.backend.service;

public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException() {
        super("レポートが見つかりません。");
    }
}
