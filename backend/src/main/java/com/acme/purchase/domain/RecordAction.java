package com.acme.purchase.domain;

/** 审批流水中的动作类型，用于审计追溯。 */
public enum RecordAction {
    CREATE("创建"),
    UPDATE("修改"),
    SUBMIT("提交"),
    APPROVE("审批通过"),
    REJECT("驳回"),
    CANCEL("作废");

    private final String label;

    RecordAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
