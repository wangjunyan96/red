package com.aimovies.platform.repo;

import com.aimovies.platform.model.PointTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    List<PointTransaction> findByUserIdOrderByIdDesc(Long userId);
}
