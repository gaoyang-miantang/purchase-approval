package com.acme.purchase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 审批流水（审计日志）。只增不改不删，任何状态变更都必须留痕。
 * 真实系统里这张表还要记录 IP、终端、操作前后快照。
 */
@Entity
@Table(name = "approval_record")
public class ApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordAction action;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RequestStatus toStatus;

    @Column(nullable = false, length = 32)
    private String operatorUsername;

    @Column(nullable = false, length = 32)
    private String operatorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role operatorRole;

    @Column(length = 300)
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;

    protected ApprovalRecord() {
    }

    public ApprovalRecord(Long requestId, RecordAction action, RequestStatus fromStatus,
                          RequestStatus toStatus, User operator, String comment) {
        this.requestId = requestId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.operatorUsername = operator.getUsername();
        this.operatorName = operator.getDisplayName();
        this.operatorRole = operator.getRole();
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public RecordAction getAction() {
        return action;
    }

    public RequestStatus getFromStatus() {
        return fromStatus;
    }

    public RequestStatus getToStatus() {
        return toStatus;
    }

    public String getOperatorUsername() {
        return operatorUsername;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public Role getOperatorRole() {
        return operatorRole;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 仅供演示数据初始化使用，业务代码不要调用。 */
    public void overrideCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
