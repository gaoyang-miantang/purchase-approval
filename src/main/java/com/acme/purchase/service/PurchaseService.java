package com.acme.purchase.service;

import com.acme.purchase.common.BizException;
import com.acme.purchase.common.PageResult;
import com.acme.purchase.config.AppProperties;
import com.acme.purchase.domain.ApprovalRecord;
import com.acme.purchase.domain.Category;
import com.acme.purchase.domain.PurchaseRequest;
import com.acme.purchase.domain.RecordAction;
import com.acme.purchase.domain.RequestStatus;
import com.acme.purchase.domain.Role;
import com.acme.purchase.domain.User;
import com.acme.purchase.repo.ApprovalRecordRepository;
import com.acme.purchase.repo.PurchaseRequestRepository;
import com.acme.purchase.web.Dto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 采购申请的核心业务逻辑。
 *
 * <p>分层原则：Controller 只做参数绑定与返回包装，所有"能不能做"的判断都在这里，
 * 保证同一套规则对 API 和未来的定时任务/消息消费都成立。
 */
@Service
public class PurchaseService {

    private final PurchaseRequestRepository requestRepo;
    private final ApprovalRecordRepository recordRepo;
    private final ApprovalPolicy policy;
    private final AppProperties properties;

    public PurchaseService(PurchaseRequestRepository requestRepo,
                           ApprovalRecordRepository recordRepo,
                           ApprovalPolicy policy,
                           AppProperties properties) {
        this.requestRepo = requestRepo;
        this.recordRepo = recordRepo;
        this.policy = policy;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ 写操作

    @Transactional
    public Dto.RequestView create(Dto.SaveRequest req, User me) {
        if (me.getRole() != Role.EMPLOYEE) {
            throw BizException.forbidden("只有员工可以发起采购申请");
        }
        PurchaseRequest entity = new PurchaseRequest();
        entity.setCode(nextCode());
        entity.setTitle(req.title().trim());
        entity.setReason(req.reason().trim());
        entity.setCategory(req.category());
        entity.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        entity.setDepartment(me.getDepartment());
        entity.setApplicantUsername(me.getUsername());
        entity.setApplicantName(me.getDisplayName());
        entity.setStatus(RequestStatus.DRAFT);

        PurchaseRequest saved = requestRepo.save(entity);
        record(saved, RecordAction.CREATE, null, RequestStatus.DRAFT, me, "创建申请单");
        return view(saved, me);
    }

    @Transactional
    public Dto.RequestView update(Long id, Dto.SaveRequest req, User me) {
        PurchaseRequest entity = load(id);
        assertOwner(entity, me);
        if (entity.getStatus() != RequestStatus.DRAFT) {
            throw BizException.conflict("只有草稿状态的申请单可以修改");
        }
        entity.setTitle(req.title().trim());
        entity.setReason(req.reason().trim());
        entity.setCategory(req.category());
        entity.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));

        PurchaseRequest saved = requestRepo.saveAndFlush(entity);
        record(saved, RecordAction.UPDATE, RequestStatus.DRAFT, RequestStatus.DRAFT, me, "修改申请单");
        return view(saved, me);
    }

    @Transactional
    public Dto.RequestView submit(Long id, User me) {
        PurchaseRequest entity = load(id);
        assertOwner(entity, me);
        if (entity.getStatus() != RequestStatus.DRAFT) {
            throw BizException.conflict("当前状态不允许提交：" + entity.getStatus().getLabel());
        }
        entity.setStatus(RequestStatus.PENDING_MANAGER);
        PurchaseRequest saved = requestRepo.saveAndFlush(entity);
        record(saved, RecordAction.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_MANAGER, me, "提交审批");
        return view(saved, me);
    }

    /**
     * 审批通过。
     *
     * <p>{@code @Transactional} + 实体 {@code @Version}：两个人同时点"通过"时，
     * 只有一个事务能提交成功，另一个会因为版本号不匹配抛出乐观锁异常，
     * 由全局异常处理器转成 409，而不是产生两条审批流水。
     */
    @Transactional
    public Dto.RequestView approve(Long id, User me, String comment, Long demoDelayMs) {
        PurchaseRequest entity = load(id);
        assertCanView(me, entity);
        if (!policy.canApprove(me, entity)) {
            if (entity.getStatus().isTerminal()) {
                throw BizException.conflict("该申请单已是终态（" + entity.getStatus().getLabel() + "），不能重复审批");
            }
            throw BizException.forbidden("您无权审批该申请单");
        }
        sleepForDemo(demoDelayMs);

        RequestStatus from = entity.getStatus();
        entity.setStatus(policy.nextAfterApprove(entity));
        PurchaseRequest saved = requestRepo.saveAndFlush(entity);
        record(saved, RecordAction.APPROVE, from, saved.getStatus(), me,
                (comment == null || comment.isBlank()) ? "同意" : comment.trim());
        return view(saved, me);
    }

    @Transactional
    public Dto.RequestView reject(Long id, User me, String reason, Long demoDelayMs) {
        PurchaseRequest entity = load(id);
        assertCanView(me, entity);
        if (!policy.canApprove(me, entity)) {
            if (entity.getStatus().isTerminal()) {
                throw BizException.conflict("该申请单已是终态（" + entity.getStatus().getLabel() + "），不能重复审批");
            }
            throw BizException.forbidden("您无权驳回该申请单");
        }
        sleepForDemo(demoDelayMs);

        RequestStatus from = entity.getStatus();
        entity.setStatus(RequestStatus.REJECTED);
        PurchaseRequest saved = requestRepo.saveAndFlush(entity);
        record(saved, RecordAction.REJECT, from, RequestStatus.REJECTED, me, reason.trim());
        return view(saved, me);
    }

    @Transactional
    public Dto.RequestView cancel(Long id, User me, String reason) {
        PurchaseRequest entity = load(id);
        assertCanView(me, entity);
        boolean isOwner = entity.getApplicantUsername().equals(me.getUsername());
        boolean isAdmin = me.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) {
            throw BizException.forbidden("只有申请人或管理员可以作废申请单");
        }
        if (entity.getStatus().isTerminal()) {
            throw BizException.conflict("申请单已结束，无法作废");
        }
        RequestStatus from = entity.getStatus();
        entity.setStatus(RequestStatus.CANCELLED);
        PurchaseRequest saved = requestRepo.saveAndFlush(entity);
        record(saved, RecordAction.CANCEL, from, RequestStatus.CANCELLED, me,
                (reason == null || reason.isBlank()) ? "申请人撤回" : reason.trim());
        return view(saved, me);
    }

    // ------------------------------------------------------------------ 读操作

    @Transactional(readOnly = true)
    public PageResult<Dto.RequestView> search(User me, String tab, String status, String keyword,
                                              String category, BigDecimal minAmount, BigDecimal maxAmount,
                                              int page, int size) {
        Specification<PurchaseRequest> spec = scope(me, tab).and(filters(status, keyword, category, minAmount, maxAmount));
        PageRequest pageRequest = PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<PurchaseRequest> result = requestRepo.findAll(spec, pageRequest);
        List<Dto.RequestView> items = result.getContent().stream().map(r -> view(r, me)).toList();
        return new PageResult<>(items, result.getTotalElements(), result.getNumber() + 1, result.getSize());
    }

    @Transactional(readOnly = true)
    public Dto.DetailView detail(Long id, User me) {
        PurchaseRequest entity = load(id);
        assertCanView(me, entity);
        List<Dto.RecordView> records = recordRepo.findByRequestIdOrderByIdAsc(id).stream()
                .map(this::recordView)
                .toList();
        return new Dto.DetailView(view(entity, me), records);
    }

    /**
     * 工作台统计。
     *
     * <p>演示规模下直接内存聚合；真实项目应下推到 SQL（GROUP BY）或走单独的统计库，
     * 否则数据量上来后这里会拖垮数据库。
     */
    @Transactional(readOnly = true)
    public Dto.StatsView stats(User me) {
        List<PurchaseRequest> visible = requestRepo.findAll(dataScope(me));
        List<PurchaseRequest> mine = requestRepo.findAll(scope(me, "mine"));
        List<PurchaseRequest> todo = requestRepo.findAll(todoScope(me));

        Instant monthStart = LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        BigDecimal monthAmount = visible.stream()
                .filter(r -> r.getStatus() == RequestStatus.APPROVED)
                .filter(r -> r.getUpdatedAt() != null && !r.getUpdatedAt().isBefore(monthStart))
                .map(PurchaseRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<RequestStatus, Long> statusCounts = new LinkedHashMap<>();
        for (RequestStatus s : RequestStatus.values()) {
            statusCounts.put(s, 0L);
        }
        visible.forEach(r -> statusCounts.merge(r.getStatus(), 1L, Long::sum));

        Map<Category, BigDecimal> categoryAmounts = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            categoryAmounts.put(c, BigDecimal.ZERO);
        }
        visible.stream()
                .filter(r -> r.getStatus() == RequestStatus.APPROVED)
                .forEach(r -> categoryAmounts.merge(r.getCategory(), r.getAmount(), BigDecimal::add));

        List<Dto.CountItem> statusItems = statusCounts.entrySet().stream()
                .map(e -> new Dto.CountItem(e.getKey().name(), e.getKey().getLabel(), e.getValue()))
                .toList();
        List<Dto.AmountItem> categoryItems = categoryAmounts.entrySet().stream()
                .map(e -> new Dto.AmountItem(e.getKey().name(), e.getKey().getLabel(), e.getValue()))
                .sorted(Comparator.comparing(Dto.AmountItem::amount).reversed())
                .toList();

        long myOngoing = mine.stream().filter(r -> r.getStatus().isPending()).count();

        return new Dto.StatsView(todo.size(), myOngoing, visible.size(), todo.size(),
                mine.size(), visible.size(), monthAmount, statusItems, categoryItems);
    }

    // ------------------------------------------------------------------ 权限与数据范围

    /** 列表可见范围：tab = mine / todo / all。 */
    private Specification<PurchaseRequest> scope(User me, String tab) {
        String normalized = (tab == null || tab.isBlank()) ? "all" : tab;
        return switch (normalized) {
            case "mine" -> (root, query, cb) -> cb.equal(root.get("applicantUsername"), me.getUsername());
            case "todo" -> todoScope(me);
            default -> dataScope(me);
        };
    }

    private Specification<PurchaseRequest> todoScope(User me) {
        return switch (me.getRole()) {
            case MANAGER -> (root, query, cb) -> cb.and(
                    cb.equal(root.get("status"), RequestStatus.PENDING_MANAGER),
                    cb.equal(root.get("department"), me.getDepartment()),
                    cb.notEqual(root.get("applicantUsername"), me.getUsername()));
            case FINANCE -> (root, query, cb) -> cb.equal(root.get("status"), RequestStatus.PENDING_FINANCE);
            default -> (root, query, cb) -> cb.disjunction();
        };
    }

    /**
     * 数据范围：同一条 SQL 对不同角色看到的数据不同。
     * 这是企业系统最容易出漏洞的地方——只在前端隐藏按钮是不够的，必须在查询层兜住。
     */
    private Specification<PurchaseRequest> dataScope(User me) {
        return switch (me.getRole()) {
            case EMPLOYEE -> (root, query, cb) -> cb.equal(root.get("applicantUsername"), me.getUsername());
            case MANAGER -> (root, query, cb) -> cb.equal(root.get("department"), me.getDepartment());
            case FINANCE -> (root, query, cb) -> cb.notEqual(root.get("status"), RequestStatus.DRAFT);
            case ADMIN -> (root, query, cb) -> cb.conjunction();
        };
    }

    private Specification<PurchaseRequest> filters(String status, String keyword, String category,
                                                   BigDecimal minAmount, BigDecimal maxAmount) {
        List<Specification<PurchaseRequest>> parts = new ArrayList<>();
        parts.add((root, query, cb) -> cb.conjunction());

        if (status != null && !status.isBlank()) {
            RequestStatus st = parseStatus(status);
            parts.add((root, query, cb) -> cb.equal(root.get("status"), st));
        }
        if (category != null && !category.isBlank()) {
            Category ct;
            try {
                ct = Category.valueOf(category.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw BizException.badRequest("未知的采购类别：" + category);
            }
            parts.add((root, query, cb) -> cb.equal(root.get("category"), ct));
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            parts.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("code")), like)));
        }
        if (minAmount != null) {
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
        }
        if (maxAmount != null) {
            parts.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
        }
        return parts.stream().reduce((a, b) -> a.and(b)).orElse((root, query, cb) -> cb.conjunction());
    }

    private RequestStatus parseStatus(String status) {
        try {
            return RequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("未知的状态：" + status);
        }
    }

    private void assertCanView(User me, PurchaseRequest entity) {
        if (me.getRole() == Role.ADMIN) {
            return;
        }
        if (entity.getApplicantUsername().equals(me.getUsername())) {
            return;
        }
        if (me.getRole() == Role.MANAGER && entity.getDepartment().equals(me.getDepartment())) {
            return;
        }
        if (me.getRole() == Role.FINANCE && entity.getStatus() != RequestStatus.DRAFT) {
            return;
        }
        throw BizException.forbidden("无权查看该申请单");
    }

    private void assertOwner(PurchaseRequest entity, User me) {
        if (!entity.getApplicantUsername().equals(me.getUsername())) {
            throw BizException.forbidden("只能操作自己发起的申请单");
        }
    }

    // ------------------------------------------------------------------ 内部工具

    private PurchaseRequest load(Long id) {
        return requestRepo.findById(id).orElseThrow(() -> BizException.notFound("申请单不存在：" + id));
    }

    private void record(PurchaseRequest request, RecordAction action, RequestStatus from,
                        RequestStatus to, User operator, String comment) {
        recordRepo.save(new ApprovalRecord(request.getId(), action, from, to, operator, comment));
    }

    private String nextCode() {
        String day = DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(ZoneId.systemDefault()).format(Instant.now());
        for (int i = 0; i < 20; i++) {
            String candidate = "PR" + day + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            if (!requestRepo.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("单号生成失败，请重试");
    }

    /** 仅演示模式生效：拉长"读-校验-写"的窗口，让并发冲突可以稳定复现。 */
    private void sleepForDemo(Long delayMs) {
        if (!properties.isDemoMode() || delayMs == null || delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(delayMs, 5000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Dto.RequestView view(PurchaseRequest r, User viewer) {
        boolean canApprove = policy.canApprove(viewer, r);
        boolean isOwner = r.getApplicantUsername().equals(viewer.getUsername());
        boolean isDraft = r.getStatus() == RequestStatus.DRAFT;
        boolean canCancel = (isOwner || viewer.getRole() == Role.ADMIN) && !r.getStatus().isTerminal();
        return new Dto.RequestView(
                r.getId(), r.getCode(), r.getTitle(), r.getReason(),
                r.getCategory().name(), r.getCategory().getLabel(), r.getAmount(),
                r.getDepartment(), r.getApplicantName(), r.getApplicantUsername(),
                r.getStatus().name(), r.getStatus().getLabel(),
                r.getCreatedAt(), r.getUpdatedAt(),
                canApprove, canApprove, isOwner && isDraft, canCancel, isOwner && isDraft,
                policy.waitingFor(r));
    }

    private Dto.RecordView recordView(ApprovalRecord r) {
        return new Dto.RecordView(
                r.getId(), r.getAction().name(), r.getAction().getLabel(),
                r.getFromStatus() == null ? null : r.getFromStatus().name(),
                r.getFromStatus() == null ? null : r.getFromStatus().getLabel(),
                r.getToStatus().name(), r.getToStatus().getLabel(),
                r.getOperatorName(), r.getOperatorRole().name(), r.getOperatorRole().getLabel(),
                r.getComment(), r.getCreatedAt());
    }
}
