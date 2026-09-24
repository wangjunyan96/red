package com.aimovies.platform.web;

import com.aimovies.platform.model.Project;
import com.aimovies.platform.repo.ProjectRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public project catalog (项目中心). */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectRepository projects;

    public ProjectController(ProjectRepository projects) {
        this.projects = projects;
    }

    @GetMapping
    public List<Project> list() {
        return projects.findAll();
    }
}
