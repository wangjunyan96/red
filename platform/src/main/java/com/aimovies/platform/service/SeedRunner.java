package com.aimovies.platform.service;

import com.aimovies.platform.model.DataAccount;
import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.Project;
import com.aimovies.platform.model.User;
import com.aimovies.platform.repo.DataAccountRepository;
import com.aimovies.platform.repo.ProjectRepository;
import com.aimovies.platform.repo.UserRepository;
import com.aimovies.platform.security.PasswordUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds a starter dataset on first run so the platform is usable immediately. */
@Component
public class SeedRunner implements ApplicationRunner {
    private final UserRepository users;
    private final ProjectRepository projects;
    private final DataAccountRepository dataAccounts;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:admin123}")
    private String adminPassword;

    public SeedRunner(UserRepository users, ProjectRepository projects, DataAccountRepository dataAccounts) {
        this.users = users;
        this.projects = projects;
        this.dataAccounts = dataAccounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedProjects();
        seedDataAccounts();
    }

    private void seedUsers() {
        if (users.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPassword(PasswordUtil.hash(adminPassword));
        admin.setRole(Enums.Role.ADMIN);
        admin.setPoints(0);
        users.save(admin);

        User demo = new User();
        demo.setUsername("user");
        demo.setPassword(PasswordUtil.hash("user123"));
        demo.setRole(Enums.Role.USER);
        demo.setPoints(100);
        users.save(demo);
    }

    private void seedProjects() {
        if (projects.count() > 0) {
            return;
        }
        addProject("和平藏宝阁-1头", "助力活动", 5, 1);
        addProject("足球在线[2头]", "助力活动", 7, 2);
        addProject("start-云游戏[10头]", "助力活动", 70, 10);
        addProject("碰碰今天(80+)", "助力活动", 18, 1);
        addProject("足球在线-0917[5头]", "未分类", 20, 5);
        addProject("zfb闪购", "未分类", 18, 1);
    }

    private void addProject(String name, String category, long price, int heads) {
        Project p = new Project();
        p.setName(name);
        p.setCategory(category);
        p.setPrice(price);
        p.setHeads(heads);
        p.setStatus(Enums.ProjectStatus.ACCEPTING);
        projects.save(p);
    }

    private void seedDataAccounts() {
        if (dataAccounts.count() > 0) {
            return;
        }
        for (int i = 1; i <= 20; i++) {
            DataAccount a = new DataAccount();
            a.setToken(String.format("tok_%04d", i));
            a.setStatus(Enums.DataAccountStatus.UNLINKED);
            dataAccounts.save(a);
        }
    }
}
