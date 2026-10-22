package jp.shirabete.backend.research;

import jp.shirabete.backend.domain.Source;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.research.mode", havingValue = "local")
public class LocalResearchProvider implements ResearchProvider {
    @Override
    public ResearchResult research(String question) {
        return new ResearchResult("""
                【ローカルデモ：実際の調査結果ではありません】
                ご質問「%s」について、調査の進め方を例示します。

                1. 用語の定義と、知りたい範囲・対象期間を整理します。
                2. 公式ドキュメントなどの一次資料を確認し、複数の資料を比較します。
                3. 確認できた事実と未確認の推測を分け、根拠のURLと確認日を記録します。

                このモードではWeb検索やAI推論を実行していません。
                参考例 [1] [2] はアプリ構成に関連する公式ドキュメントであり、
                ご質問の回答を裏付ける出典ではありません。実際の調査には外部プロバイダーの実装が必要です。
                """.formatted(question).strip(), List.of(
                new Source("参考例 [1] Spring Boot 公式ドキュメント（質問の根拠ではありません）",
                        "https://docs.spring.io/spring-boot/"),
                new Source("参考例 [2] Microsoft Foundry 公式ドキュメント（質問の根拠ではありません）",
                        "https://learn.microsoft.com/ja-jp/azure/ai-foundry/")));
    }
}
