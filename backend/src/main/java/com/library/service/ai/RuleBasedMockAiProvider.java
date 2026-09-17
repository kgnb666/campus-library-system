package com.library.service.ai;

import com.library.domain.entity.Book;
import com.library.dto.ai.BookInsightResponse;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地规则与启发式智能导读生成器 (Stage 5 离线测试与弹性容灾兜底提供者)
 */
@Component("ruleBasedMockAiProvider")
public class RuleBasedMockAiProvider implements AiProvider {

    @Override
    public BookInsightResponse generateInsight(Book book) {
        String categoryName = book.getCategory() != null ? book.getCategory().getName() : "综合通识";
        String author = book.getAuthor() != null ? book.getAuthor() : "名家作者";
        String title = book.getTitle();

        // 提炼核心简介
        String summary;
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            summary = "《" + title + "》是由" + author + "所著的" + categoryName + "领域经典佳作。本书系统阐述了" +
                    (book.getDescription().length() > 100 ? book.getDescription().substring(0, 100) + "..." : book.getDescription());
        } else {
            summary = "《" + title + "》由" + author + "倾力编撰，是" + categoryName + "类别中极具借阅与研读价值的重要著作。";
        }

        // 核心主题 Chip 标签
        List<String> keyTopics = new ArrayList<>();
        keyTopics.add(categoryName);
        keyTopics.add(author + "代表作");
        keyTopics.add("专业经典研读");
        keyTopics.add("理论与实践指南");

        // 目标受众分析
        String targetReader;
        if (categoryName.contains("计算") || categoryName.contains("软件") || categoryName.contains("信息")) {
            targetReader = "计算机及相关专业高年级本科生、软件研发工程师及系统架构研习者。";
        } else if (categoryName.contains("文学") || categoryName.contains("艺术") || categoryName.contains("历史")) {
            targetReader = "人文社科类学生、文学阅读爱好者及通识素养拓展研习读者。";
        } else if (categoryName.contains("经济") || categoryName.contains("管理")) {
            targetReader = "经管类在校师生、商业策略分析学习者及创新创业探索者。";
        } else {
            targetReader = "全校广大师生读者、学术通识钻研者及自我提升学习者。";
        }

        // 阅读建议
        String readingGuide = "建议先浏览全书目录建立宏观知识树，重点结合典型案例进行深入精读，并在阅读后梳理核心脑图以巩固学习成效。";

        return BookInsightResponse.builder()
                .bookId(book.getId())
                .bookTitle(book.getTitle())
                .summary(summary)
                .keyTopics(keyTopics)
                .targetReader(targetReader)
                .readingGuide(readingGuide)
                .modelName(getProviderName())
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    @Override
    public String generateRecommendationReason(Book book, String reasonContext) {
        if (reasonContext != null && !reasonContext.isBlank()) {
            return reasonContext;
        }
        return "全馆高频借阅经典书目，深度契合校园学术研读脉络。";
    }

    @Override
    public String getProviderName() {
        return "rule-based-mock";
    }
}
