package com.acme.purchase.repo;

import com.acme.purchase.domain.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    List<ApprovalRecord> findByRequestIdOrderByIdAsc(Long requestId);
}
