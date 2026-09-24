package com.aimovies.platform.repo;

import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.Task;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByOrderId(Long orderId);

    long countByOrderIdAndStatus(Long orderId, Enums.TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.status = :pending "
        + "OR (t.status = :running AND t.leaseUntil < :now) ORDER BY t.id ASC")
    List<Task> findClaimable(@Param("pending") Enums.TaskStatus pending,
                             @Param("running") Enums.TaskStatus running,
                             @Param("now") long now,
                             Pageable pageable);

    @Query("SELECT t.status AS status, COUNT(t) AS c FROM Task t GROUP BY t.status")
    List<Object[]> countGroupByStatus();
}
