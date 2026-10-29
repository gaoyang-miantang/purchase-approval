package com.acme.purchase.web;

import com.acme.purchase.domain.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 接口出入参对象。全部用 record，天然不可变，避免实体被外部直接改写。
 */
public final class Dto {

    private Dto() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record UserView(Long id, String username, String displayName, String department,
                           String role, String roleLabel, String deptAndRole) {
    }

    public record LoginResponse(String token, UserView user) {
    }

    public record SaveRequest(
            @NotBlank(message = "标题不能为空")
            @Size(max = 120, message = "标题不能超过 120 字") String title,
            @NotBlank(message = "申请事由不能为空")
            @Size(max = 500, message = "申请事由不能超过 500 字") String reason,
            @NotNull(message = "请选择采购类别") Category category,
            @NotNull(message = "金额不能为空")
            @DecimalMin(value = "0.01", message = "金额必须大于 0")
            @Digits(integer = 12, fraction = 2, message = "金额最多两位小数") BigDecimal amount) {
    }

    public record ApproveRequest(@Size(max = 300, message = "审批意见不能超过 300 字") String comment) {
    }

    public record RejectRequest(
            @NotBlank(message = "驳回必须填写原因")
            @Size(max = 300, message = "驳回原因不能超过 300 字") String reason) {
    }

    public record CancelRequest(@Size(max = 300, message = "作废原因不能超过 300 字") String reason) {
    }

    /** 列表行数据：额外带上"当前用户能对它做什么"，前端据此渲染按钮，避免按钮点了才报错。 */
    public record RequestView(
            Long id, String code, String title, String reason,
            String category, String categoryLabel, BigDecimal amount,
            String department, String applicantName, String applicantUsername,
            String status, String statusLabel,
            Instant createdAt, Instant updatedAt,
            boolean canApprove, boolean canReject, boolean canSubmit, boolean canCancel, boolean canEdit,
            String waitingFor) {
    }

    public record RecordView(
            Long id, String action, String actionLabel,
            String fromStatus, String fromStatusLabel,
            String toStatus, String toStatusLabel,
            String operatorName, String operatorRole, String operatorRoleLabel,
            String comment, Instant createdAt) {
    }

    public record DetailView(RequestView request, List<RecordView> records) {
    }

    public record CountItem(String status, String label, long count) {
    }

    public record AmountItem(String category, String label, BigDecimal amount) {
    }

    public record StatsView(long pendingForMe, long myOngoing, long total, long todoCount,
                            long mineCount, long allCount, BigDecimal monthApprovedAmount,
                            List<CountItem> statusCounts, List<AmountItem> categoryAmounts) {
    }
}
