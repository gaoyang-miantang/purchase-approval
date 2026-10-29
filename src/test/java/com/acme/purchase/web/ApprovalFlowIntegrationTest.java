package com.acme.purchase.web;

import com.acme.purchase.domain.Category;
import com.acme.purchase.domain.PurchaseRequest;
import com.acme.purchase.domain.RecordAction;
import com.acme.purchase.domain.RequestStatus;
import com.acme.purchase.domain.Role;
import com.acme.purchase.domain.User;
import com.acme.purchase.repo.ApprovalRecordRepository;
import com.acme.purchase.repo.PurchaseRequestRepository;
import com.acme.purchase.repo.UserRepository;
import com.acme.purchase.service.PurchaseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端集成测试：走真实的 HTTP 层、真实事务、真实 H2 数据库。
 * 与 {@link com.acme.purchase.service.ApprovalPolicyTest} 互补——这里验证的是"接线"是否正确。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("审批流程集成测试")
class ApprovalFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private PurchaseRequestRepository requestRepo;

    @Autowired
    private ApprovalRecordRepository recordRepo;

    @Autowired
    private PurchaseService purchaseService;

    private User employee;
    private User manager;
    private User otherManager;
    private User finance;
    private String employeeToken;
    private String managerToken;
    private String otherManagerToken;
    private String financeToken;

    @BeforeEach
    void setUp() throws Exception {
        recordRepo.deleteAll();
        requestRepo.deleteAll();
        userRepo.deleteAll();

        employee = userRepo.save(new User("t_emp", "测试员工", "研发部", Role.EMPLOYEE, "123456"));
        manager = userRepo.save(new User("t_mgr", "测试主管", "研发部", Role.MANAGER, "123456"));
        otherManager = userRepo.save(new User("t_mgr2", "外部主管", "市场部", Role.MANAGER, "123456"));
        finance = userRepo.save(new User("t_fin", "测试财务", "财务部", Role.FINANCE, "123456"));

        employeeToken = login("t_emp");
        managerToken = login("t_mgr");
        otherManagerToken = login("t_mgr2");
        financeToken = login("t_fin");
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("token").asText();
    }

    private String createBody(String title, String amount) {
        return "{\"title\":\"" + title + "\",\"reason\":\"集成测试用例\","
                + "\"category\":\"OFFICE\",\"amount\":" + amount + "}";
    }

    private long createRequest(String title, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(title, amount)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }

    @Test
    @DisplayName("小额申请：创建 → 提交 → 主管审批 → 通过")
    void smallAmountFlow() throws Exception {
        long id = createRequest("小额采购", "1200");

        mockMvc.perform(post("/api/requests/" + id + "/submit")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_MANAGER"));

        mockMvc.perform(post("/api/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("5000 元档：主管通过后进入待财务复核，财务通过后才终态")
    void largeAmountNeedsFinance() throws Exception {
        long id = createRequest("大额采购", "5000");
        mockMvc.perform(post("/api/requests/" + id + "/submit")
                .header("Authorization", "Bearer " + employeeToken));

        mockMvc.perform(post("/api/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.data.status").value("PENDING_FINANCE"));

        mockMvc.perform(post("/api/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + financeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"预算内\"}"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("跨部门主管审批 → 403")
    void crossDepartmentApproveForbidden() throws Exception {
        long id = createRequest("跨部门越权", "800");
        mockMvc.perform(post("/api/requests/" + id + "/submit")
                .header("Authorization", "Bearer " + employeeToken));

        mockMvc.perform(post("/api/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + otherManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("员工调用审批接口 → 403")
    void employeeApproveForbidden() throws Exception {
        long id = createRequest("员工越权", "600");
        mockMvc.perform(post("/api/requests/" + id + "/submit")
                .header("Authorization", "Bearer " + employeeToken));

        mockMvc.perform(post("/api/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("没有令牌访问受保护接口 → 401")
    void missingTokenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("非法入参（空标题 + 负金额）→ 400")
    void invalidPayloadReturns400() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"reason\":\"\",\"category\":\"OFFICE\",\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("驳回必须填写原因 → 400")
    void rejectWithoutReasonReturns400() throws Exception {
        long id = createRequest("驳回无原因", "500");
        mockMvc.perform(post("/api/requests/" + id + "/submit")
                .header("Authorization", "Bearer " + employeeToken));

        mockMvc.perform(post("/api/requests/" + id + "/reject")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("终态单据重复审批 → 409")
    void approveTerminalRequestReturns409() throws Exception {
        long id = createRequest("重复审批", "300");
        mockMvc.perform(post("/api/requests/" + id + "/submit")
                .header("Authorization", "Bearer " + employeeToken));
        mockMvc.perform(post("/api/requests/" + id + "/approve")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        mockMvc.perform(post("/api/requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("数据范围：员工列表只能看到自己的单据")
    void employeeSeesOnlyOwnRequests() throws Exception {
        createRequest("我的单子", "100");

        mockMvc.perform(get("/api/requests?tab=all")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/requests?tab=all")
                        .header("Authorization", "Bearer " + otherManagerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    /**
     * 并发审批：两个线程同时审批同一张单。
     * 期望恰好一个成功、一个因乐观锁失败，且审批流水只产生一条 APPROVE。
     */
    @Test
    @DisplayName("两人同时审批同一单：仅一人成功，流水不重复")
    void concurrentApproveOnlyOneWins() throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setCode("PR-CONCURRENT-0001");
        request.setTitle("并发测试单");
        request.setReason("并发用例");
        request.setCategory(Category.IT_ASSET);
        request.setAmount(new BigDecimal("300"));
        request.setDepartment("研发部");
        request.setApplicantUsername(employee.getUsername());
        request.setApplicantName(employee.getDisplayName());
        request.setStatus(RequestStatus.PENDING_MANAGER);
        PurchaseRequest saved = requestRepo.save(request);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLine = new CountDownLatch(1);
        Callable<Object> task = () -> {
            startLine.await(5, TimeUnit.SECONDS);
            try {
                return purchaseService.approve(saved.getId(), manager, "并发审批", 500L);
            } catch (Exception e) {
                return e;
            }
        };

        Future<Object> first = pool.submit(task);
        Future<Object> second = pool.submit(task);
        startLine.countDown();

        List<Object> results = new ArrayList<>();
        results.add(first.get(20, TimeUnit.SECONDS));
        results.add(second.get(20, TimeUnit.SECONDS));
        pool.shutdown();

        long success = results.stream().filter(r -> r instanceof Dto.RequestView).count();
        long failed = results.stream().filter(r -> r instanceof Exception).count();

        assertThat(success).as("应恰好一个请求成功").isEqualTo(1);
        assertThat(failed).as("应恰好一个请求失败").isEqualTo(1);

        Object failure = results.stream().filter(r -> r instanceof Exception).findFirst().orElseThrow();
        assertThat(failure)
                .as("失败原因应为乐观锁/锁等待，而不是业务规则异常")
                .isInstanceOfAny(OptimisticLockingFailureException.class, CannotAcquireLockException.class);

        long approveRecords = recordRepo.findByRequestIdOrderByIdAsc(saved.getId()).stream()
                .filter(r -> r.getAction() == RecordAction.APPROVE)
                .count();
        assertThat(approveRecords).as("审批流水不能重复").isEqualTo(1);
    }
}
