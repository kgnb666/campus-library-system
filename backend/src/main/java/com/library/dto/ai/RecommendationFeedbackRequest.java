package com.library.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 读者对推荐卡片反馈评价请求 (Stage 5)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationFeedbackRequest {

    @NotBlank(message = "反馈状态不可为空")
    @Pattern(regexp = "^(LIKE|DISLIKE|NEUTRAL)$", message = "反馈类型必须为 LIKE, DISLIKE 或 NEUTRAL")
    private String feedback;
}
