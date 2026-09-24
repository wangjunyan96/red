package com.aimovies.platform.repo;

import com.aimovies.platform.model.ReunionCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReunionCodeRepository extends JpaRepository<ReunionCode, Long> {
    Optional<ReunionCode> findByReunionCode(String reunionCode);
}
