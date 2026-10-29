package com.acme.purchase.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 业务可配置项。金额阈值等规则参数不写死在代码里，
 * 后续换预算口径只需要改配置，不用改代码、不用重新评审逻辑。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 演示模式：允许通过请求头人为制造并发窗口，仅用于现场演示。生产必须关闭。 */
    private boolean demoMode = false;

    private Approval approval = new Approval();

    public boolean isDemoMode() {
        return demoMode;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public Approval getApproval() {
        return approval;
    }

    public void setApproval(Approval approval) {
        this.approval = approval;
    }

    public static class Approval {

        /** 金额达到该值（含）时，主管审批后还需财务复核。 */
        private BigDecimal financeThreshold = new BigDecimal("5000");

        public BigDecimal getFinanceThreshold() {
            return financeThreshold;
        }

        public void setFinanceThreshold(BigDecimal financeThreshold) {
            this.financeThreshold = financeThreshold;
        }
    }
}
