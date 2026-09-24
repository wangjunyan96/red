package com.aimovies.platform.repo;

import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByStatusOrderByIdAsc(Enums.ProjectStatus status);
}
