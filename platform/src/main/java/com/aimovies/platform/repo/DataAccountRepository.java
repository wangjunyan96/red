package com.aimovies.platform.repo;

import com.aimovies.platform.model.DataAccount;
import com.aimovies.platform.model.Enums;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataAccountRepository extends JpaRepository<DataAccount, Long> {
    List<DataAccount> findByStatusOrderByIdAsc(Enums.DataAccountStatus status, Pageable pageable);

    boolean existsByToken(String token);

    long countByStatus(Enums.DataAccountStatus status);
}
