package com.acme.purchase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 企业采购申请审批平台 —— AI Coding 实战案例。
 *
 * 启动后访问 http://localhost:8080 即可进入演示界面。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PurchaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PurchaseApplication.class, args);
    }
}
