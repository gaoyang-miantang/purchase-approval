package com.acme.purchase.common;

import java.util.List;

/**
 * 分页结果。企业前端表格必须分页，不能把全表一次性丢给浏览器。
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }
}
