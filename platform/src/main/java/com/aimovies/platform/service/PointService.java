package com.aimovies.platform.service;

import com.aimovies.platform.model.Enums;
import com.aimovies.platform.model.PointTransaction;
import com.aimovies.platform.model.User;
import com.aimovies.platform.repo.PointTransactionRepository;
import com.aimovies.platform.repo.UserRepository;
import com.aimovies.platform.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {
    private final UserRepository users;
    private final PointTransactionRepository ledger;

    public PointService(UserRepository users, PointTransactionRepository ledger) {
        this.users = users;
        this.ledger = ledger;
    }

    /** Apply a signed delta to a user's points and record a ledger entry. */
    @Transactional
    public synchronized long adjust(Long userId, long delta, Enums.PointTxType type, String remark) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("user not found"));
        long next = user.getPoints() + delta;
        if (next < 0) {
            throw ApiException.badRequest("insufficient points");
        }
        user.setPoints(next);
        users.save(user);
        ledger.save(new PointTransaction(userId, type, delta, next, remark));
        return next;
    }

    /** Set a user's points to an absolute value (admin action). */
    @Transactional
    public synchronized long set(Long userId, long value, String remark) {
        if (value < 0) {
            throw ApiException.badRequest("points cannot be negative");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("user not found"));
        long delta = value - user.getPoints();
        user.setPoints(value);
        users.save(user);
        ledger.save(new PointTransaction(userId, Enums.PointTxType.ADMIN_ADJUST, delta, value, remark));
        return value;
    }
}
