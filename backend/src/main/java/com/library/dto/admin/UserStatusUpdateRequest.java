package com.library.dto.admin;

import com.library.domain.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员变更用户状态请求 (Stage 10-O)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusUpdateRequest {

    /** 目标状态：ACTIVE（启用）/ DISABLED（停用） */
    @NotNull(message = "目标状态不能为空")
    private UserStatus status;
}
