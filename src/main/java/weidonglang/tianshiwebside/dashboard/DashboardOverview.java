package weidonglang.tianshiwebside.dashboard;

import java.util.List;

public record DashboardOverview(
        String roleView,
        String term,
        String scopeLabel,
        List<DashboardCard> cards,
        int courseCount,
        int pendingEvaluationCount,
        int examCount,
        int earnedCredits,
        List<DashboardEventRow> recentEvents
) {
    public record DashboardCard(
            String key,
            String label,
            int value,
            String suffix,
            String scope
    ) {
    }
}
