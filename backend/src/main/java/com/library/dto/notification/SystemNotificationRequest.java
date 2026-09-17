package com.library.dto.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 馆员/管理员发布系统公告请求 DTO (Stage 6-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemNotificationRequest {

    /** 目标读者 ID (为空则面向全体活跃读者广播) */
    private Long targetUserId;

    @NotBlank(message = "通知标题不能为空")
    @Size(max = 128, message = "通知标题长度不能超过128字符")
    private String title;

    @NotBlank(message = "通知内容不能为空")
    private String content;
}
