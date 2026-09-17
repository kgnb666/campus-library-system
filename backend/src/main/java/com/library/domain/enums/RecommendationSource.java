package com.library.domain.enums;

import lombok.Getter;

/**
 * 推荐来源枚举 (Stage 5)
 */
@Getter
public enum RecommendationSource {
    CONTENT_BASED("内容特征匹配"),
    BEHAVIOR_COLLABORATIVE("读者行为协同"),
    POPULARITY("全馆热门精选"),
    HYBRID_AI("AI混合多路加权");

    private final String description;

    RecommendationSource(String description) {
        this.description = description;
    }
}
