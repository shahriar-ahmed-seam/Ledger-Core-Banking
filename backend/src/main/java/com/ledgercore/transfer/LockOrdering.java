package com.ledgercore.transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic global lock-acquisition ordering. Locks are always acquired in ascending
 * order of account identifier so that two transactions touching the same set of accounts
 * can never form a hold-and-wait cycle (Requirement 7.6).
 */
public final class LockOrdering {

    private LockOrdering() {
    }

    /**
     * @return the distinct account identifiers sorted ascending, the order in which their
     *         row locks must be acquired.
     */
    public static List<UUID> order(UUID... accountIds) {
        List<UUID> distinct = new ArrayList<>();
        for (UUID id : accountIds) {
            if (id != null && !distinct.contains(id)) {
                distinct.add(id);
            }
        }
        distinct.sort(UUID::compareTo);
        return distinct;
    }
}
