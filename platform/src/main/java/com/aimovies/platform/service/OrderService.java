package com.aimovies.platform.service;

import com.aimovies.platform.model.DataAccount;
import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.OrderEntity;
import com.aimovies.platform.model.Project;
import com.aimovies.platform.model.ReunionCode;
import com.aimovies.platform.model.Task;
import com.aimovies.platform.model.User;
import com.aimovies.platform.repo.DataAccountRepository;
import com.aimovies.platform.repo.OrderRepository;
import com.aimovies.platform.repo.ProjectRepository;
import com.aimovies.platform.repo.ReunionCodeRepository;
import com.aimovies.platform.repo.TaskRepository;
import com.aimovies.platform.web.ApiException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final DataAccountRepository dataAccounts;
    private final ReunionCodeRepository reunionCodes;
    private final PointService pointService;

    public OrderService(OrderRepository orders, ProjectRepository projects, TaskRepository tasks,
                        DataAccountRepository dataAccounts, ReunionCodeRepository reunionCodes,
                        PointService pointService) {
        this.orders = orders;
        this.projects = projects;
        this.tasks = tasks;
        this.dataAccounts = dataAccounts;
        this.reunionCodes = reunionCodes;
        this.pointService = pointService;
    }

    /**
     * Place an order: parse the code, reserve N data accounts, deduct points,
     * create the order and its N worker tasks, atomically.
     */
    @Transactional
    public synchronized OrderEntity placeOrder(User user, Long projectId, String rawCode) {
        Project project = projects.findById(projectId)
            .orElseThrow(() -> ApiException.notFound("project not found"));
        if (project.getStatus() != Enums.ProjectStatus.ACCEPTING) {
            throw ApiException.badRequest("project is not accepting orders");
        }

        String code = InviteCodeParser.parse(rawCode);
        int heads = project.getHeads();

        if (user.getPoints() < project.getPrice()) {
            throw ApiException.badRequest("insufficient points: need " + project.getPrice()
                + ", have " + user.getPoints());
        }

        List<DataAccount> available = dataAccounts.findByStatusOrderByIdAsc(
            Enums.DataAccountStatus.UNLINKED, PageRequest.of(0, heads));
        if (available.size() < heads) {
            throw ApiException.conflict("insufficient data accounts: need " + heads
                + ", have " + available.size());
        }

        // Deduct points (throws if insufficient) then build the order.
        pointService.adjust(user.getId(), -project.getPrice(), Enums.PointTxType.CONSUME,
            "order for " + project.getName());

        OrderEntity order = new OrderEntity();
        order.setOrderNo(generateOrderNo());
        order.setUserId(user.getId());
        order.setProjectId(project.getId());
        order.setProjectName(project.getName());
        order.setInviteCode(code);
        order.setAmount(project.getPrice());
        order.setHeads(heads);
        order.setStatus(Enums.OrderStatus.PROCESSING);
        order = orders.save(order);

        if (reunionCodes.findByReunionCode(code).isEmpty()) {
            reunionCodes.save(new ReunionCode(code));
        }

        for (DataAccount account : available) {
            account.setStatus(Enums.DataAccountStatus.LINKED);
            account.setReunionCode(code);
            dataAccounts.save(account);

            Task task = new Task();
            task.setOrderId(order.getId());
            task.setAccount(account.getToken());
            task.setReunionCode(code);
            task.setDataAccountId(account.getId());
            task.setStatus(Enums.TaskStatus.PENDING);
            tasks.save(task);
        }

        return order;
    }

    private String generateOrderNo() {
        for (int i = 0; i < 20; i++) {
            String candidate = "ZN" + ThreadLocalRandom.current().nextInt(100, 1000);
            if (orders.findByOrderNo(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "ZN" + System.currentTimeMillis();
    }

    public List<OrderEntity> listForUser(Long userId) {
        return orders.findByUserIdOrderByIdDesc(userId);
    }

    public OrderEntity getForUser(Long userId, String orderNo) {
        OrderEntity order = orders.findByOrderNo(orderNo)
            .orElseThrow(() -> ApiException.notFound("order not found"));
        if (!order.getUserId().equals(userId)) {
            throw ApiException.forbidden("not your order");
        }
        return order;
    }
}
