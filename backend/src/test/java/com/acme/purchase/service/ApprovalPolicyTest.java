package com.acme.purchase.service;

import com.acme.purchase.config.AppProperties;
import com.acme.purchase.domain.Category;
import com.acme.purchase.domain.PurchaseRequest;
import com.acme.purchase.domain.RequestStatus;
import com.acme.purchase.domain.Role;
import com.acme.purchase.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审批规则的纯单元测试：不启动 Spring，只验证规则本身。
 * 这类测试跑得最快，也最应该在改规则时先跑。
 */
@DisplayName("审批规则 ApprovalPolicy")
class ApprovalPolicyTest {

    private ApprovalPolicy policy;
    private User manager;
    private User otherManager;
    private User finance;
    private User employee;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getApproval().setFinanceThreshold(new BigDecimal("5000"));
        policy = new ApprovalPolicy(properties);

        manager = new User("lijing", "李静", "研发部", Role.MANAGER, "x");
        otherManager = new User("zhaolei", "赵磊", "市场部", Role.MANAGER, "x");
        finance = new User("wangfang", "王芳", "财务部", Role.FINANCE, "x");
        employee = new User("zhangwei", "张伟", "研发部", Role.EMPLOYEE, "x");
    }

    private PurchaseRequest request(String amount, RequestStatus status, String applicant) {
        PurchaseRequest request = new PurchaseRequest();
        request.setCode("PR-TEST-0001");
        request.setTitle("测试单");
        request.setReason("测试");
        request.setCategory(Category.OFFICE);
        request.setAmount(new BigDecimal(amount));
        request.setDepartment("研发部");
        request.setApplicantUsername(applicant);
        request.setApplicantName("张伟");
        request.setStatus(status);
        return request;
    }

    @Test
    @DisplayName("4999.99 元：主管审批后直接通过")
    void belowThresholdGoesStraightToApproved() {
        PurchaseRequest request = request("4999.99", RequestStatus.PENDING_MANAGER, "zhangwei");
        assertThat(policy.nextAfterApprove(request)).isEqualTo(RequestStatus.APPROVED);
    }

    @Test
    @DisplayName("5000.00 元整：必须进入财务复核（边界含等于）")
    void exactlyThresholdRequiresFinance() {
        PurchaseRequest request = request("5000.00", RequestStatus.PENDING_MANAGER, "zhangwei");
        assertThat(policy.nextAfterApprove(request)).isEqualTo(RequestStatus.PENDING_FINANCE);
    }

    @Test
    @DisplayName("5000.01 元：进入财务复核")
    void aboveThresholdRequiresFinance() {
        PurchaseRequest request = request("5000.01", RequestStatus.PENDING_MANAGER, "zhangwei");
        assertThat(policy.nextAfterApprove(request)).isEqualTo(RequestStatus.PENDING_FINANCE);
    }

    @Test
    @DisplayName("本部门主管可以审批")
    void sameDepartmentManagerCanApprove() {
        PurchaseRequest request = request("100", RequestStatus.PENDING_MANAGER, "zhangwei");
        assertThat(policy.canApprove(manager, request)).isTrue();
    }

    @Test
    @DisplayName("其他部门主管不能审批")
    void otherDepartmentManagerCannotApprove() {
        PurchaseRequest request = request("100", RequestStatus.PENDING_MANAGER, "zhangwei");
        assertThat(policy.canApprove(otherManager, request)).isFalse();
    }

    @Test
    @DisplayName("主管不能审批自己提交的单据")
    void managerCannotApproveOwnRequest() {
        PurchaseRequest request = request("100", RequestStatus.PENDING_MANAGER, "lijing");
        assertThat(policy.canApprove(manager, request)).isFalse();
    }

    @Test
    @DisplayName("财务只能审批待财务复核的单据")
    void financeOnlyHandlesFinanceStage() {
        PurchaseRequest atManager = request("8000", RequestStatus.PENDING_MANAGER, "zhangwei");
        PurchaseRequest atFinance = request("8000", RequestStatus.PENDING_FINANCE, "zhangwei");
        assertThat(policy.canApprove(finance, atManager)).isFalse();
        assertThat(policy.canApprove(finance, atFinance)).isTrue();
    }

    @Test
    @DisplayName("员工没有任何审批权")
    void employeeHasNoApprovalRight() {
        PurchaseRequest request = request("100", RequestStatus.PENDING_MANAGER, "zhangwei");
        assertThat(policy.canApprove(employee, request)).isFalse();
        assertThat(policy.canApprove(employee, request("8000", RequestStatus.PENDING_FINANCE, "zhangwei"))).isFalse();
    }

    @Test
    @DisplayName("已通过的终态单据不能再审批")
    void terminalRequestCannotBeApproved() {
        PurchaseRequest request = request("100", RequestStatus.APPROVED, "zhangwei");
        assertThat(policy.canApprove(manager, request)).isFalse();
    }
}
