package com.acme.purchase.domain;

/**
 * 申请单状态机。
 *
 * <pre>
 * DRAFT --submit--> PENDING_MANAGER --approve(金额 < 阈值)--> APPROVED
 *                          |
 *                          +--approve(金额 >= 阈值)--> PENDING_FINANCE --approve--> APPROVED
 *
 * 任意非终态 --reject--> REJECTED
 * 任意非终态 --cancel--> CANCELLED
 * </pre>
 */
public enum RequestStatus {
    DRAFT("草稿"),
    PENDING_MANAGER("待主管审批"),
    PENDING_FINANCE("待财务复核"),
    APPROVED("已通过"),
    REJECTED("已驳回"),
    CANCELLED("已作废");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 终态不可再变更，任何写操作都必须先做这个判断。 */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }

    public boolean isPending() {
        return this == PENDING_MANAGER || this == PENDING_FINANCE;
    }
}
