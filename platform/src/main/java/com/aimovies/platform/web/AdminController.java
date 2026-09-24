package com.aimovies.platform.web;

import com.aimovies.platform.model.DataAccount;
import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.OrderEntity;
import com.aimovies.platform.model.Project;
import com.aimovies.platform.model.User;
import com.aimovies.platform.repo.DataAccountRepository;
import com.aimovies.platform.repo.OrderRepository;
import com.aimovies.platform.repo.ProjectRepository;
import com.aimovies.platform.repo.UserRepository;
import com.aimovies.platform.security.AuthSupport;
import com.aimovies.platform.service.PointService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final OrderRepository orders;
    private final DataAccountRepository dataAccounts;
    private final PointService pointService;
    private final AuthSupport auth;

    public AdminController(UserRepository users, ProjectRepository projects, OrderRepository orders,
                           DataAccountRepository dataAccounts, PointService pointService, AuthSupport auth) {
        this.users = users;
        this.projects = projects;
        this.orders = orders;
        this.dataAccounts = dataAccounts;
        this.pointService = pointService;
        this.auth = auth;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers(HttpServletRequest request) {
        auth.requireAdmin(request);
        return users.findAll().stream().map(Views::user).collect(Collectors.toList());
    }

    /** Edit points: provide either {"set": N} (absolute) or {"delta": N} (relative). */
    @PostMapping("/users/{id}/points")
    public Map<String, Object> editPoints(@PathVariable Long id,
                                          @RequestBody Dtos.AdminPointsRequest req,
                                          HttpServletRequest request) {
        auth.requireAdmin(request);
        String remark = req.remark() == null ? "admin adjust" : req.remark();
        long points;
        if (req.set() != null) {
            points = pointService.set(id, req.set(), remark);
        } else if (req.delta() != null) {
            points = pointService.adjust(id, req.delta(), Enums.PointTxType.ADMIN_ADJUST, remark);
        } else {
            throw ApiException.badRequest("provide either 'set' or 'delta'");
        }
        return Map.of("userId", id, "points", points);
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> listOrders(HttpServletRequest request) {
        auth.requireAdmin(request);
        List<Map<String, Object>> out = new ArrayList<>();
        for (OrderEntity o : orders.findAll()) {
            Map<String, Object> m = Views.order(o);
            m.put("userId", o.getUserId());
            out.add(m);
        }
        return out;
    }

    @GetMapping("/projects")
    public List<Project> listProjects(HttpServletRequest request) {
        auth.requireAdmin(request);
        return projects.findAll();
    }

    @PostMapping("/projects")
    public Project createProject(@RequestBody Dtos.ProjectRequest req, HttpServletRequest request) {
        auth.requireAdmin(request);
        if (req.name() == null || req.name().isBlank() || req.price() == null) {
            throw ApiException.badRequest("name and price are required");
        }
        Project p = new Project();
        applyProject(p, req);
        return projects.save(p);
    }

    @PutMapping("/projects/{id}")
    public Project updateProject(@PathVariable Long id, @RequestBody Dtos.ProjectRequest req,
                                 HttpServletRequest request) {
        auth.requireAdmin(request);
        Project p = projects.findById(id).orElseThrow(() -> ApiException.notFound("project not found"));
        applyProject(p, req);
        return projects.save(p);
    }

    @DeleteMapping("/projects/{id}")
    public Map<String, Object> deleteProject(@PathVariable Long id, HttpServletRequest request) {
        auth.requireAdmin(request);
        projects.deleteById(id);
        return Map.of("ok", true);
    }

    private void applyProject(Project p, Dtos.ProjectRequest req) {
        if (req.name() != null) {
            p.setName(req.name());
        }
        if (req.category() != null) {
            p.setCategory(req.category());
        }
        if (req.price() != null) {
            p.setPrice(req.price());
        }
        if (req.heads() != null) {
            p.setHeads(req.heads());
        }
        if (req.tutorialUrl() != null) {
            p.setTutorialUrl(req.tutorialUrl());
        }
        if (req.status() != null) {
            try {
                p.setStatus(Enums.ProjectStatus.valueOf(req.status().trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("status must be ACCEPTING or STOPPED");
            }
        }
    }

    @GetMapping("/data-accounts")
    public Map<String, Object> listDataAccounts(HttpServletRequest request) {
        auth.requireAdmin(request);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", dataAccounts.count());
        m.put("unlinked", dataAccounts.countByStatus(Enums.DataAccountStatus.UNLINKED));
        m.put("linked", dataAccounts.countByStatus(Enums.DataAccountStatus.LINKED));
        m.put("bound", dataAccounts.countByStatus(Enums.DataAccountStatus.BOUND));
        m.put("error", dataAccounts.countByStatus(Enums.DataAccountStatus.ERROR));
        m.put("accounts", dataAccounts.findAll());
        return m;
    }

    @PostMapping("/data-accounts")
    public Map<String, Object> addDataAccounts(@RequestBody Dtos.DataAccountsRequest req,
                                               HttpServletRequest request) {
        auth.requireAdmin(request);
        if (req.tokens() == null || req.tokens().isEmpty()) {
            throw ApiException.badRequest("tokens are required");
        }
        int added = 0;
        for (String token : req.tokens()) {
            if (token == null || token.isBlank() || dataAccounts.existsByToken(token.trim())) {
                continue;
            }
            DataAccount a = new DataAccount();
            a.setToken(token.trim());
            a.setStatus(Enums.DataAccountStatus.UNLINKED);
            dataAccounts.save(a);
            added++;
        }
        return Map.of("added", added, "total", dataAccounts.count());
    }
}
