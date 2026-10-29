package com.acme.purchase.config;

import com.acme.purchase.domain.ApprovalRecord;
import com.acme.purchase.domain.Category;
import com.acme.purchase.domain.PurchaseRequest;
import com.acme.purchase.domain.RecordAction;
import com.acme.purchase.domain.RequestStatus;
import com.acme.purchase.domain.Role;
import com.acme.purchase.domain.User;
import com.acme.purchase.repo.ApprovalRecordRepository;
import com.acme.purchase.repo.PurchaseRequestRepository;
import com.acme.purchase.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 演示数据初始化。
 *
 * <p>刻意造出了覆盖全部状态、跨部门、跨金额档位的样本，
 * 这样任何一种角色的数据范围和审批链都能当场演示，不需要手工造数。
 */
@Component
public class SeedData implements CommandLineRunner {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneId.systemDefault());

    private final UserRepository userRepo;
    private final PurchaseRequestRepository requestRepo;
    private final ApprovalRecordRepository recordRepo;

    private int seq = 0;

    public SeedData(UserRepository userRepo, PurchaseRequestRepository requestRepo,
                    ApprovalRecordRepository recordRepo) {
        this.userRepo = userRepo;
        this.requestRepo = requestRepo;
        this.recordRepo = recordRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepo.count() > 0) {
            return;
        }

        User zhangwei = userRepo.save(new User("zhangwei", "张伟", "研发部", Role.EMPLOYEE, "123456"));
        User lijing = userRepo.save(new User("lijing", "李静", "研发部", Role.MANAGER, "123456"));
        User wangfang = userRepo.save(new User("wangfang", "王芳", "财务部", Role.FINANCE, "123456"));
        User chenming = userRepo.save(new User("chenming", "陈明", "综合管理部", Role.ADMIN, "123456"));        User liuyang = userRepo.save(new User("liuyang", "刘洋", "市场部", Role.EMPLOYEE, "123456"));
        User zhaolei = userRepo.save(new User("zhaolei", "赵磊", "市场部", Role.MANAGER, "123456"));

        Instant now = Instant.now();

        // 1. 待主管审批（研发部，小额）
        Instant t1 = now.minus(Duration.ofHours(26));
        PurchaseRequest r1 = create(zhangwei, "研发部 Q4 办公用品采购",
                "打印纸、硒鼓、签字笔等常规消耗品补充，覆盖 10-12 月用量",
                Category.OFFICE, "2340.00", RequestStatus.PENDING_MANAGER, t1, t1.plus(Duration.ofHours(1)));
        record(r1, RecordAction.CREATE, null, RequestStatus.DRAFT, zhangwei, "创建申请单", t1);
        record(r1, RecordAction.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_MANAGER, zhangwei,
                "提交审批", t1.plus(Duration.ofHours(1)));

        // 2. 草稿（员工本人可见）
        Instant t2 = now.minus(Duration.ofHours(3));
        PurchaseRequest r2 = create(zhangwei, "开发用 4K 显示器 2 台",
                "新入职两名后端工程师，需配置 27 寸 4K 显示器",
                Category.IT_ASSET, "4899.00", RequestStatus.DRAFT, t2, t2);
        record(r2, RecordAction.CREATE, null, RequestStatus.DRAFT, zhangwei, "创建申请单", t2);

        // 3. 待财务复核（研发部，超过 5000 阈值，已过主管）
        Instant t3 = now.minus(Duration.ofHours(50));
        PurchaseRequest r3 = create(zhangwei, "测试服务器内存扩容 16 条",
                "压测环境内存不足，需从 128G 扩容至 256G",
                Category.IT_ASSET, "8600.00", RequestStatus.PENDING_FINANCE, t3, t3.plus(Duration.ofHours(4)));
        record(r3, RecordAction.CREATE, null, RequestStatus.DRAFT, zhangwei, "创建申请单", t3);
        record(r3, RecordAction.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_MANAGER, zhangwei,
                "提交审批", t3.plus(Duration.ofMinutes(30)));
        record(r3, RecordAction.APPROVE, RequestStatus.PENDING_MANAGER, RequestStatus.PENDING_FINANCE,
                lijing, "金额超过 5000，转财务复核", t3.plus(Duration.ofHours(4)));

        // 4. 已通过（小额，主管一级审批）
        Instant t4 = now.minus(Duration.ofHours(96));
        PurchaseRequest r4 = create(zhangwei, "技术图书采购（年度）",
                "团队年度技术书籍采购，人均 2 本",
                Category.OFFICE, "1280.00", RequestStatus.APPROVED, t4, t4.plus(Duration.ofHours(4)));
        record(r4, RecordAction.CREATE, null, RequestStatus.DRAFT, zhangwei, "创建申请单", t4);
        record(r4, RecordAction.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_MANAGER, zhangwei,
                "提交审批", t4.plus(Duration.ofMinutes(20)));
        record(r4, RecordAction.APPROVE, RequestStatus.PENDING_MANAGER, RequestStatus.APPROVED,
                lijing, "同意，按预算执行", t4.plus(Duration.ofHours(4)));

        // 5. 已作废
        Instant t5 = now.minus(Duration.ofHours(120));
        PurchaseRequest r5 = create(zhangwei, "临时团建物料",
                "原计划团建活动已取消",
                Category.OFFICE, "980.00", RequestStatus.CANCELLED, t5, t5.plus(Duration.ofHours(2)));
        record(r5, RecordAction.CREATE, null, RequestStatus.DRAFT, zhangwei, "创建申请单", t5);
        record(r5, RecordAction.CANCEL, RequestStatus.DRAFT, RequestStatus.CANCELLED, zhangwei,
                "活动取消，撤回申请", t5.plus(Duration.ofHours(2)));

        // 6. 市场部待主管审批（跨部门数据范围演示）
        Instant t6 = now.minus(Duration.ofHours(8));
        PurchaseRequest r6 = create(liuyang, "秋季行业展会物料制作",
                "展位搭建、易拉宝、宣传册 2000 份",
                Category.SERVICE, "12800.00", RequestStatus.PENDING_MANAGER, t6, t6.plus(Duration.ofHours(1)));
        record(r6, RecordAction.CREATE, null, RequestStatus.DRAFT, liuyang, "创建申请单", t6);
        record(r6, RecordAction.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_MANAGER, liuyang,
                "提交审批", t6.plus(Duration.ofHours(1)));

        // 7. 已驳回
        Instant t7 = now.minus(Duration.ofHours(70));
        PurchaseRequest r7 = create(liuyang, "上海客户拜访差旅",
                "重点客户季度拜访，含高铁与两晚住宿",
                Category.TRAVEL, "3600.00", RequestStatus.REJECTED, t7, t7.plus(Duration.ofHours(6)));
        record(r7, RecordAction.CREATE, null, RequestStatus.DRAFT, liuyang, "创建申请单", t7);
        record(r7, RecordAction.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_MANAGER, liuyang,
                "提交审批", t7.plus(Duration.ofMinutes(40)));
        record(r7, RecordAction.REJECT, RequestStatus.PENDING_MANAGER, RequestStatus.REJECTED, zhaolei,
                "本季度差旅预算已用尽，请下季度再提交", t7.plus(Duration.ofHours(6)));

        // 8. 已通过（两级审批：主管 + 财务）
        Instant t8 = now.minus(Duration.ofHours(140));
        PurchaseRequest r8 = create(liuyang, "市场部直播设备采购",
                "短视频直播所需补光灯、无线麦克风、采集卡",
                Category.IT_ASSET, "6200.00", RequestStatus.APPROVED, t8, t8.plus(Duration.ofHours(10)));
        record(r8, RecordAction.CREATE, null, RequestStatus.DRAFT, liuyang, "创建申请单", t8);
        record(r8, RecordAction.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_MANAGER, liuyang,
                "提交审批", t8.plus(Duration.ofMinutes(15)));
        record(r8, RecordAction.APPROVE, RequestStatus.PENDING_MANAGER, RequestStatus.PENDING_FINANCE,
                zhaolei, "业务需要，同意", t8.plus(Duration.ofHours(3)));
        record(r8, RecordAction.APPROVE, RequestStatus.PENDING_FINANCE, RequestStatus.APPROVED,
                wangfang, "预算内，同意付款", t8.plus(Duration.ofHours(10)));

        // 9. 市场部待财务复核（大额）
        Instant t9 = now.minus(Duration.ofHours(30));
        PurchaseRequest r9 = create(liuyang, "年度客户答谢会场地",
                "12 月客户答谢会场地租赁及餐饮",
                Category.SERVICE, "15000.00", RequestStatus.PENDING_FINANCE, t9, t9.plus(Duration.ofHours(4)));
        record(r9, RecordAction.CREATE, null, RequestStatus.DRAFT, liuyang, "创建申请单", t9);
        record(r9, RecordAction.SUBMIT, RequestStatus.DRAFT, RequestStatus.PENDING_MANAGER, liuyang,
                "提交审批", t9.plus(Duration.ofMinutes(25)));
        record(r9, RecordAction.APPROVE, RequestStatus.PENDING_MANAGER, RequestStatus.PENDING_FINANCE,
                zhaolei, "同意，请财务复核预算", t9.plus(Duration.ofHours(4)));
    }

    private PurchaseRequest create(User applicant, String title, String reason, Category category,
                                   String amount, RequestStatus status, Instant createdAt, Instant updatedAt) {
        PurchaseRequest entity = new PurchaseRequest();
        entity.setCode("PR" + DAY.format(createdAt) + "-" + String.format("%04d", ++seq));
        entity.setTitle(title);
        entity.setReason(reason);
        entity.setCategory(category);
        entity.setAmount(new BigDecimal(amount));
        entity.setDepartment(applicant.getDepartment());
        entity.setApplicantUsername(applicant.getUsername());
        entity.setApplicantName(applicant.getDisplayName());
        entity.setStatus(status);
        entity.overrideTimestamps(createdAt, updatedAt);
        return requestRepo.save(entity);
    }

    private void record(PurchaseRequest request, RecordAction action, RequestStatus from,
                        RequestStatus to, User operator, String comment, Instant at) {
        ApprovalRecord record = new ApprovalRecord(request.getId(), action, from, to, operator, comment);
        record.overrideCreatedAt(at);
        recordRepo.save(record);
    }
}
