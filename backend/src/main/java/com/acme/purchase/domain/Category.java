package com.acme.purchase.domain;

public enum Category {
    OFFICE("办公用品"),
    IT_ASSET("IT设备"),
    TRAVEL("差旅"),
    SERVICE("服务采购");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
