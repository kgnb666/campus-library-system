package com.library.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.common.enums.ResultCode;
import com.library.exception.BusinessException;
import com.library.domain.entity.AiBookInsight;
import com.library.domain.entity.Book;
import com.library.dto.ai.BookInsightResponse;
import com.library.repository.AiBookInsightRepository;
import com.library.repository.BookRepository;
import com.library.service.AiInsightService;
import com.library.service.ai.AiProvider;
import com.library.service.ai.BookInsightContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 智能导读服务实现 (Stage 5，Stage 10-F 事务边界与并发修复)
 *
 * <p>设计意图: 外部大模型 HTTP 调用耗时可达数秒，必须放在数据库事务之外执行，
 * 否则长事务会长期占用连接池。代价是调用期间 Session 已关闭，
 * 因此 Provider 只能接收事先抽好的不可变上下文（见 {@link BookInsightContext}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInsightServiceImpl implements AiInsightService {

    private final AiBookInsightRepository aiBookInsightRepository;
    private final BookRepository bookRepository;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final AiInsightTransactionHelper transactionHelper;

    /**
     * 图书级并发生成互斥锁槽位数。
     *
     * <p>原实现使用 {@code ConcurrentHashMap<Long,Object>} 并在 finally 中
     * {@code remove(bookId, lock)}：A 持锁、B 在锁上等待期间 A 结束并移除条目，
     * C 到达时 computeIfAbsent 会建出一个**新锁对象**并与 B 同时进入临界区
     * —— 双检失效，同一书目仍可能调用两次外部模型。</p>
     *
     * <p>现改为固定槽位的条带锁（与 Guava Striped 同思路，无需新增依赖）：
     * 锁对象恒定存在、不存在"可被移除"的窗口，且内存占用有界。
     * 不同书目可能命中同一槽位而被无谓串行化，属可接受的取舍。</p>
     */
    private static final int LOCK_STRIPES = 64;
    private final Object[] bookLockStripes = createStripes();

    private static Object[] createStripes() {
        Object[] stripes = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            stripes[i] = new Object();
        }
        return stripes;
    }

    private Object lockFor(Long bookId) {
        return bookLockStripes[Math.floorMod(bookId, LOCK_STRIPES)];
    }

    @Override
    public BookInsightResponse getBookInsight(Long bookId) {
        BookInsightContext context = loadContext(bookId);

        // Step 1: 优先读取数据库持久化缓存 (只读快速路径，零长事务，零网络开销)
        Optional<AiBookInsight> existingOpt = aiBookInsightRepository.findByBookId(bookId);
        if (existingOpt.isPresent()) {
            return convertToDto(existingOpt.get(), context);
        }

        // Step 2 & 3: 并发防重复生成保护 (按书目串行)
        synchronized (lockFor(bookId)) {
            // Double Check: 确认在排队等待锁期间，前面的线程是否已经完成生成并入库
            Optional<AiBookInsight> doubleCheck = aiBookInsightRepository.findByBookId(bookId);
            if (doubleCheck.isPresent()) {
                return convertToDto(doubleCheck.get(), context);
            }

            // 首次生成：在事务外调用外部大模型 HTTP (网络耗时不持有任何 DB 物理连接)
            log.info("首次为图书 id={}, title='{}' 调用 AI 生成导读 (事务外执行)", context.bookId(), context.title());
            BookInsightResponse generated = aiProvider.generateInsight(context);

            // Step 4: 调用独立短事务持久化保存
            AiBookInsight savedEntity = transactionHelper.saveOrUpdateInsight(bookId, generated);
            return convertToDto(savedEntity, context);
        }
    }

    @Override
    public BookInsightResponse refreshBookInsight(Long bookId) {
        BookInsightContext context = loadContext(bookId);

        synchronized (lockFor(bookId)) {
            log.info("管理员请求重新生成图书 id={}, title='{}' 的 AI 导读 (事务外执行)", context.bookId(), context.title());
            BookInsightResponse generated = aiProvider.generateInsight(context);

            // 独立短事务更新
            AiBookInsight savedEntity = transactionHelper.saveOrUpdateInsight(bookId, generated);
            return convertToDto(savedEntity, context);
        }
    }

    /**
     * 在持有会话的边界内加载图书并抽出不可变上下文。
     *
     * <p>使用 fetch join 一次性初始化 {@code category}，随后立即读取所需字段；
     * 这样后续的事务外网络调用不会再触碰任何懒加载关联。</p>
     */
    private BookInsightContext loadContext(Long bookId) {
        Book book = bookRepository.findByIdWithCategory(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));
        return BookInsightContext.of(book);
    }

    private BookInsightResponse convertToDto(AiBookInsight entity, BookInsightContext context) {
        List<String> topics;
        try {
            topics = objectMapper.readValue(entity.getKeyTopics(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            topics = new ArrayList<>();
        }

        return BookInsightResponse.builder()
                .id(entity.getId())
                .bookId(context.bookId())
                .bookTitle(context.title())
                .summary(entity.getSummary())
                .keyTopics(topics)
                .targetReader(entity.getTargetReader())
                .readingGuide(entity.getReadingGuide())
                .modelName(entity.getModelName())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }
}
