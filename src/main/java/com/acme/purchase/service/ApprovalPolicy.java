package com.acme.purchase.service;

import com.acme.purchase.config.AppProperties;
import com.acme.purchase.domain.PurchaseRequest;
import com.acme.purchase.domain.RequestStatus;
import com.acme.purchase.domain.Role;
import com.acme.purchase.domain.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 审批规则集中在这里，和"怎么存库""怎么返回"完全解耦。
 *
 * <p>这样拆的好处：规则可以单独写单元测试，改规则时不会碰到 Controller，
 * 也方便把规则表驱动化（不同金额区间走不同审批链）。
 */
@Component
public class ApprovalPolicy {

    private final BigDecimal financeThreshold;

    public ApprovalPolicy(AppProperties properties) {
        this.financeThreshold = properties.getApproval().getFinanceThreshold();
    }

    /**
     * 判断某人当前能否审批这张单。
     *
     * <p>包含三条约束，缺一不可：
     * <ol>
     *   <li>状态对得上（待主管 / 待财务）</li>
     *   <li>角色对得上，且主管只能审本部门</li>
     *   <li>不能审批自己提交的单据（防止自审自批）</li>
     * </ol>
     */
    public boolean canApprove(User user, PurchaseRequest request) {
        if (request.getStatus() == RequestStatus.PENDING_MANAGER) {
            return user.getRole() == Role.MANAGER
                    && request.getDepartment().equals(user.getDepartment())
                    && !request.getApplicantUsername().equals(user.getUsername());
        }
        if (request.getStatus() == RequestStatus.PENDING_FINANCE) {
            return user.getRole() == Role.FINANCE;
        }
        return false;
    }

    /**
     * 审批通过后的下一个状态。
     * 注意这里是 {@code >=}：金额正好等于阈值时也必须走财务复核。
     */
    public RequestStatus nextAfterApprove(PurchaseRequest request) {
        if (request.getStatus() == RequestStatus.PENDING_MANAGER
                && request.getAmount().compareTo(financeThreshold) >= 0) {
            return RequestStatus.PENDING_FINANCE;
        }
        return RequestStatus.APPROVED;
    }

    /** 当前单据在等谁处理，用于前端提示。 */
    public String waitingFor(PurchaseRequest request) {
        return switch (request.getStatus()) {
            case PENDING_MANAGER -> "部门主管";
            case PENDING_FINANCE -> "财务";
            case DRAFT -> "申请人提交";
            default -> null;
        };
    }

    public BigDecimal getFinanceThreshold() {
        return financeThreshold;
    }
}
