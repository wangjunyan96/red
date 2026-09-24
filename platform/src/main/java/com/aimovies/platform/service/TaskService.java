package com.aimovies.platform.service;

import com.aimovies.platform.model.DataAccount;
import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.OrderEntity;
import com.aimovies.platform.model.ReunionCode;
import com.aimovies.platform.model.Task;
import com.aimovies.platform.repo.DataAccountRepository;
import com.aimovies.platform.repo.OrderRepository;
import com.aimovies.platform.repo.ReunionCodeRepository;
import com.aimovies.platform.repo.TaskRepository;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Worker-facing dispatch: claim/heartbeat/report, plus order aggregation. */
@Service
public class TaskService {
    private static final long LEASE_SECONDS = 300L;

    private final TaskRepository tasks;
    private final OrderRepository orders;
    private final DataAccountRepository dataAccounts;
    private final ReunionCodeRepository reunionCodes;
    private final PointService pointService;

    public TaskService(TaskRepository tasks, OrderRepository orders, DataAccountRepository dataAccounts,
                       ReunionCodeRepository reunionCodes, PointService pointService) {
        this.tasks = tasks;
        this.orders = orders;
        this.dataAccounts = dataAccounts;
        this.reunionCodes = reunionCodes;
        this.pointService = pointService;
    }

    @Transactional
    public synchronized Task claim(String deviceId) {
        long now = Instant.now().getEpochSecond();
        List<Task> candidates = tasks.findClaimable(
            Enums.TaskStatus.PENDING, Enums.TaskStatus.RUNNING, now, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return null;
        }
        Task task = candidates.get(0);
        task.setStatus(Enums.TaskStatus.RUNNING);
        task.setAssignedDevice(deviceId);
        task.setRunId(UUID.randomUUID().toString());
        task.setLeaseUntil(now + LEASE_SECONDS);
        task.setAttempts(task.getAttempts() + 1);
        return tasks.save(task);
    }

    @Transactional
    public synchronized boolean heartbeat(Long taskId, String deviceId, String runId) {
        Task task = tasks.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != Enums.TaskStatus.RUNNING) {
            return false;
        }
        if (!deviceId.equals(task.getAssignedDevice()) || !runId.equals(task.getRunId())) {
            return false;
        }
        task.setLeaseUntil(Instant.now().getEpochSecond() + LEASE_SECONDS);
        tasks.save(task);
        return true;
    }

    @Transactional
    public synchronized boolean report(Long taskId, String deviceId, String runId, String status, String error) {
        Task task = tasks.findById(taskId).orElse(null);
        if (task == null || task.getStatus() != Enums.TaskStatus.RUNNING) {
            return false;
        }
        if (!deviceId.equals(task.getAssignedDevice()) || !runId.equals(task.getRunId())) {
            return false;
        }

        if ("done".equalsIgnoreCase(status)) {
            task.setStatus(Enums.TaskStatus.DONE);
            task.setLastError(null);
            task.setLeaseUntil(0);
            tasks.save(task);
            bindDataAccount(task);
            maybeCompleteOrder(task.getOrderId());
            return true;
        }
        if ("failed".equalsIgnoreCase(status)) {
            task.setStatus(Enums.TaskStatus.FAILED);
            task.setLastError(error);
            task.setLeaseUntil(0);
            tasks.save(task);
            errorDataAccount(task);
            failOrderAndRefund(task.getOrderId(), error);
            return true;
        }
        return false;
    }

    private void bindDataAccount(Task task) {
        dataAccounts.findById(task.getDataAccountId()).ifPresent(acc -> {
            acc.setStatus(Enums.DataAccountStatus.BOUND);
            dataAccounts.save(acc);
        });
        ReunionCode rc = reunionCodes.findByReunionCode(task.getReunionCode())
            .orElseGet(() -> new ReunionCode(task.getReunionCode()));
        rc.setBindCount(rc.getBindCount() + 1);
        reunionCodes.save(rc);
    }

    private void errorDataAccount(Task task) {
        dataAccounts.findById(task.getDataAccountId()).ifPresent(acc -> {
            acc.setStatus(Enums.DataAccountStatus.ERROR);
            dataAccounts.save(acc);
        });
    }

    private void maybeCompleteOrder(Long orderId) {
        OrderEntity order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != Enums.OrderStatus.PROCESSING) {
            return;
        }
        long done = tasks.countByOrderIdAndStatus(orderId, Enums.TaskStatus.DONE);
        if (done >= order.getHeads()) {
            order.setStatus(Enums.OrderStatus.SUCCESS);
            order.setFinishedAt(Instant.now());
            orders.save(order);
        }
    }

    private void failOrderAndRefund(Long orderId, String error) {
        OrderEntity order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != Enums.OrderStatus.PROCESSING) {
            return; // already finalized; refund at most once
        }
        order.setStatus(Enums.OrderStatus.FAILED);
        order.setError(error);
        order.setFinishedAt(Instant.now());
        orders.save(order);

        // Refund the full amount for a failed order.
        pointService.adjust(order.getUserId(), order.getAmount(), Enums.PointTxType.REFUND,
            "refund for order " + order.getOrderNo());

        // Cancel sibling tasks still in flight and release their (non-bound) accounts.
        for (Task sibling : tasks.findByOrderId(orderId)) {
            if (sibling.getStatus() == Enums.TaskStatus.PENDING
                || sibling.getStatus() == Enums.TaskStatus.RUNNING) {
                sibling.setStatus(Enums.TaskStatus.FAILED);
                sibling.setLastError("cancelled: order failed");
                sibling.setLeaseUntil(0);
                tasks.save(sibling);
                dataAccounts.findById(sibling.getDataAccountId()).ifPresent(acc -> {
                    if (acc.getStatus() == Enums.DataAccountStatus.LINKED) {
                        acc.setStatus(Enums.DataAccountStatus.UNLINKED);
                        acc.setReunionCode(null);
                        dataAccounts.save(acc);
                    }
                });
            }
        }
    }

    public Map<Enums.TaskStatus, Long> stats() {
        Map<Enums.TaskStatus, Long> map = new EnumMap<>(Enums.TaskStatus.class);
        for (Object[] row : tasks.countGroupByStatus()) {
            map.put((Enums.TaskStatus) row[0], (Long) row[1]);
        }
        return map;
    }
}
