package com.aimovies.platform.repo;

import com.aimovies.platform.model.OrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    List<OrderEntity> findByUserIdOrderByIdDesc(Long userId);

    Optional<OrderEntity> findByOrderNo(String orderNo);
}
