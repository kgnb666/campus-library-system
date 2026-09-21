package com.library.monitoring;

import com.library.common.constants.CommonConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路 TraceId MDC 注入过滤器 (最高优先级)
 *
 * <p>Stage 10-H: 对外部传入的 TraceId 做格式校验。
 * 原实现直接信任请求头中的任意字符串并写入 MDC 与响应头，
 * 可被注入超长内容或控制字符，造成日志污染、日志切割与审计误导。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 合法 TraceId：8~64 位十六进制字符（覆盖 UUID 去横线形式） */
    private static final java.util.regex.Pattern TRACE_ID_PATTERN =
            java.util.regex.Pattern.compile("^[0-9a-fA-F]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(CommonConstants.TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId) || !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            // 缺失或格式非法（含超长、控制字符）一律重新生成，不采信外部输入
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        MDC.put(CommonConstants.TRACE_ID_MDC_KEY, traceId);
        response.setHeader(CommonConstants.TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CommonConstants.TRACE_ID_MDC_KEY);
        }
    }
}
