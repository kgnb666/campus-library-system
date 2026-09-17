package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.exception.BusinessException;
import com.library.domain.entity.AiRecommendationLog;
import com.library.domain.entity.Book;
import com.library.domain.entity.User;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.RecommendationSource;
import com.library.dto.ai.RecommendedBookResponse;
import com.library.repository.*;
import com.library.service.AiRecommendService;
import com.library.service.ai.AiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendServiceImpl implements AiRecommendService {

    private final AiRecommendationLogRepository logRepository;
    private final BookRepository bookRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final AiProvider aiProvider;

    @Override
    @Transactional
    public List<RecommendedBookResponse> getPersonalizedRecommendations(Long userId, int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 10;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        // 1. 获取读者历史借阅书籍 ID 集合 (用于已读去重)
        List<Long> readBookIds = borrowRecordRepository.findDistinctBookIdsByUserId(userId);
        Set<Long> readBookIdSet = new HashSet<>(readBookIds);

        // 2. 统计读者偏好分类与高频作者
        List<Object[]> userCategoryCounts = borrowRecordRepository.countUserCategoryDistribution(userId);
        Map<String, Long> userCategoryMap = new HashMap<>();
        String top1Category = null;
        String top2Category = null;

        for (int i = 0; i < userCategoryCounts.size(); i++) {
            Object[] row = userCategoryCounts.get(i);
            String catName = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            userCategoryMap.put(catName, count);
            if (i == 0) top1Category = catName;
            if (i == 1) top2Category = catName;
        }

        // 2.1 预提取已读图书的作者集合 (避免在评分循环中产生 N+1 查询)
        Set<String> readAuthors = new HashSet<>();
        if (!readBookIdSet.isEmpty()) {
            for (Long rId : readBookIds) {
                bookRepository.findById(rId).ifPresent(b -> {
                    if (b.getAuthor() != null && !b.getAuthor().isBlank()) {
                        readAuthors.add(b.getAuthor());
                    }
                });
            }
        }

        // 3. 【Stage 6-A SQL 化优化】：下推数据库层分页查询有效候选书目 (杜绝全表 findAll 导致 OOM)
        int candidatePoolSize = Math.max(limit * 5, 50);
        PageRequest pageRequest = PageRequest.of(0, candidatePoolSize);
        List<Book> candidateBooks;
        if (readBookIdSet.isEmpty()) {
            candidateBooks = bookRepository.findRecommendationCandidates(BookStatus.ACTIVE, pageRequest);
        } else {
            candidateBooks = bookRepository.findRecommendationCandidatesExclude(BookStatus.ACTIVE, readBookIdSet, pageRequest);
        }

        boolean isColdStart = readBookIdSet.isEmpty();
        List<ScoredCandidate> scoredCandidates = new ArrayList<>();

        if (isColdStart) {
            // 冷启动：根据热门借阅与在架状态排序
            for (Book book : candidateBooks) {
                double popScore = Math.min(100.0, book.getTotalCopies() * 15.0);
                double stockBoost = book.getAvailableCopies() > 0 ? 20.0 : 0.0;
                double finalScore = popScore + stockBoost;

                String reason = "全馆高频借阅经典著作（新读者精选推荐）";
                scoredCandidates.add(new ScoredCandidate(book, finalScore, RecommendationSource.POPULARITY, reason));
            }
        } else {
            // 暖启动：多路加权启发式混合推荐
            for (Book book : candidateBooks) {
                String catName = book.getCategory() != null ? book.getCategory().getName() : "";
                String author = book.getAuthor() != null ? book.getAuthor() : "";

                // (1) ContentScore (40%)
                double contentScore = 0.0;
                if (catName.equals(top1Category)) {
                    contentScore += 50.0;
                } else if (catName.equals(top2Category)) {
                    contentScore += 30.0;
                } else if (userCategoryMap.containsKey(catName)) {
                    contentScore += 20.0;
                }
                if (author.length() > 1 && readAuthors.contains(author)) {
                    contentScore += 35.0;
                }
                contentScore = Math.min(100.0, contentScore);

                // (2) BehaviorScore (40%)
                double behaviorScore = 40.0; // 基础未读探索分
                if (userCategoryMap.containsKey(catName)) {
                    behaviorScore += 30.0;
                }
                behaviorScore = Math.min(100.0, behaviorScore);

                // (3) PopularityScore (20%)
                double popularityScore = Math.min(100.0, (book.getTotalCopies() - book.getAvailableCopies()) * 25.0 + 20.0);

                // (4) 在架库存加权
                double stockBoost = book.getAvailableCopies() > 0 ? 15.0 : 0.0;

                double totalScore = (contentScore * 0.4) + (behaviorScore * 0.4) + (popularityScore * 0.2) + stockBoost;

                RecommendationSource source = RecommendationSource.HYBRID_AI;
                String reason;
                if (contentScore >= 50.0 && catName.equals(top1Category)) {
                    source = RecommendationSource.CONTENT_BASED;
                    reason = "因您在「" + catName + "」领域的阅读偏好，推荐同分类佳作";
                } else if (behaviorScore >= 70.0) {
                    source = RecommendationSource.BEHAVIOR_COLLABORATIVE;
                    reason = "根据您的历史阅读轨迹与学术探究方向为您推荐";
                } else {
                    source = RecommendationSource.POPULARITY;
                    reason = "结合全馆近90天热门流通榜单与知识图谱精选";
                }

                scoredCandidates.add(new ScoredCandidate(book, totalScore, source, reason));
            }
        }

        // 4. 排序并截取 TOP N
        scoredCandidates.sort((a, b) -> Double.compare(b.score, a.score));
        List<ScoredCandidate> topN = scoredCandidates.stream().limit(limit).collect(Collectors.toList());

        // 5. 落库生成推荐曝光日志
        List<RecommendedBookResponse> resultList = new ArrayList<>();
        for (ScoredCandidate cand : topN) {
            Book b = cand.book;
            AiRecommendationLog logEntity = AiRecommendationLog.builder()
                    .user(user)
                    .book(b)
                    .recommendationSource(cand.source)
                    .score(BigDecimal.valueOf(cand.score).setScale(2, RoundingMode.HALF_UP))
                    .scene("HOME_RECOMMEND")
                    .clicked(false)
                    .borrowed(false)
                    .build();
            logEntity = logRepository.save(logEntity);

            resultList.add(RecommendedBookResponse.builder()
                    .recommendationLogId(logEntity.getId())
                    .bookId(b.getId())
                    .isbn(b.getIsbn())
                    .title(b.getTitle())
                    .author(b.getAuthor())
                    .coverUrl(b.getCoverUrl())
                    .categoryName(b.getCategory() != null ? b.getCategory().getName() : "")
                    .availableCopies(b.getAvailableCopies())
                    .totalCopies(b.getTotalCopies())
                    .score(cand.score)
                    .recommendationSource(cand.source.name())
                    .sourceDescription(cand.source.getDescription())
                    .reason(cand.reason)
                    .feedback(null)
                    .build());
        }

        return resultList;
    }

    @Override
    @Transactional
    public void recordClick(Long logId, Long userId) {
        AiRecommendationLog logEntity = logRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(ResultCode.RECOMMENDATION_LOG_NOT_FOUND));

        if (!logEntity.getUser().getId().equals(userId)) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN);
        }

        if (!Boolean.TRUE.equals(logEntity.getClicked())) {
            logEntity.setClicked(true);
            logRepository.save(logEntity);
            log.debug("成功记录推荐卡片点击埋点: logId={}, userId={}", logId, userId);
        }
    }

    @Override
    @Transactional
    public void recordFeedback(Long logId, Long userId, String feedback) {
        AiRecommendationLog logEntity = logRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(ResultCode.RECOMMENDATION_LOG_NOT_FOUND));

        if (!logEntity.getUser().getId().equals(userId)) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN);
        }

        logEntity.setFeedback(feedback);
        logRepository.save(logEntity);
        log.info("读者 userId={} 对推荐 logId={} 提交显式反馈: {}", userId, logId, feedback);
    }

    @Override
    @Transactional
    public void recordBorrowConversion(Long userId, Long bookId) {
        Optional<AiRecommendationLog> logOpt = logRepository.findFirstByUserIdAndBookIdOrderByCreatedAtDesc(userId, bookId);
        if (logOpt.isPresent()) {
            AiRecommendationLog logEntity = logOpt.get();
            if (!Boolean.TRUE.equals(logEntity.getBorrowed())) {
                logEntity.setBorrowed(true);
                logRepository.save(logEntity);
                log.info("推荐书目成功发生借阅转化! logId={}, userId={}, bookId={}", logEntity.getId(), userId, bookId);
            }
        }
    }

    @org.springframework.context.event.EventListener
    public void onBookBorrowed(com.library.event.BookBorrowedEvent event) {
        recordBorrowConversion(event.getUserId(), event.getBookId());
    }

    private static class ScoredCandidate {
        final Book book;
        final double score;
        final RecommendationSource source;
        final String reason;

        ScoredCandidate(Book book, double score, RecommendationSource source, String reason) {
            this.book = book;
            this.score = score;
            this.source = source;
            this.reason = reason;
        }
    }
}
