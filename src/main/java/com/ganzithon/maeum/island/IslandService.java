package com.ganzithon.maeum.island;

import com.ganzithon.maeum.global.activity.ActivityApi;
import com.ganzithon.maeum.global.activity.ActivityStore;
import com.ganzithon.maeum.global.activity.Idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
@Profile("auth")
@Transactional
class IslandService {
    private final IslandItemRepository items;
    private final IslandPurchaseRepository purchases;
    private final ActivityStore store;
    private final Idempotency idem;
    private final Clock clock;

    IslandService(
            IslandItemRepository items,
            IslandPurchaseRepository purchases,
            ActivityStore store,
            Idempotency idem,
            Clock clock) {
        this.items = items;
        this.purchases = purchases;
        this.store = store;
        this.idem = idem;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    IslandItemsResponse items(long user) {
        Set<Long> owned = purchases.findItemIdsByUserId(user);
        var list =
                items.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                        .map(i -> IslandItemResponse.of(i, owned.contains(i.getId())))
                        .toList();
        var wallet = store.one("SELECT balance FROM wool_wallets WHERE user_id=?", user);
        return new IslandItemsResponse(
                wallet == null ? 0 : ((Number) wallet.get("balance")).intValue(), list);
    }

    /** 사용자 행을 잠가 같은 사용자의 양털 차감을 직렬화한 뒤 멱등 처리합니다. */
    Idempotency.Reply purchase(long user, long itemId, String key) {
        store.lockUser(user);
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return idem.execute(
                user,
                key,
                "POST /api/v1/island/items/" + itemId + "/purchases",
                null,
                now,
                201,
                () -> {
                    var item =
                            items.findByIdAndActiveTrue(itemId)
                                    .orElseThrow(
                                            () ->
                                                    ActivityApi.problem(
                                                            404, "ISLAND_ITEM_NOT_FOUND"));
                    if (purchases.existsByUserIdAndItemId(user, itemId))
                        throw ActivityApi.problem(409, "ISLAND_ITEM_ALREADY_OWNED");
                    var wallet =
                            store.one(
                                    "SELECT balance FROM wool_wallets WHERE user_id=? FOR UPDATE",
                                    user);
                    int balance =
                            wallet == null ? 0 : ((Number) wallet.get("balance")).intValue();
                    int price = item.getPrice();
                    if (balance < price)
                        throw ActivityApi.problem(409, "WOOL_BALANCE_INSUFFICIENT");
                    long transaction =
                            store.insert(
                                    "INSERT INTO"
                                        + " wool_transactions(user_id,amount,transaction_type,created_at)"
                                        + " VALUES(?,?,'SPEND',?)",
                                    user,
                                    -price,
                                    ActivityApi.ts(now));
                    store.update(
                            "UPDATE wool_wallets SET balance=?,updated_at=? WHERE user_id=?",
                            balance - price,
                            ActivityApi.ts(now),
                            user);
                    try {
                        purchases.saveAndFlush(
                                new IslandPurchase(user, itemId, price, transaction, now));
                    } catch (DataIntegrityViolationException e) {
                        throw ActivityApi.problem(409, "ISLAND_ITEM_ALREADY_OWNED");
                    }
                    return new IslandPurchaseResponse(
                            IslandItemResponse.of(item, true), balance - price);
                });
    }
}
