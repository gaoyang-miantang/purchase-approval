package com.acme.purchase.domain;

/**
 * 系统角色。企业系统的权限控制围绕角色 + 数据范围两个维度展开。
 */
public enum Role {
    EMPLOYEE("员工"),
    MANAGER("部门主管"),
    FINANCE("财务"),
    ADMIN("管理员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
