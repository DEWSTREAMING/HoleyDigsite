package com.holeydigsite.tier;

import java.util.List;

/** A family of tiers (Mining Drill, Sluice Box, ...): its name and its ordered tier definitions. */
public record TierFamily(String name, List<TierDef> tiers) {
    public TierDef tier(String id) {
        for (TierDef t : tiers) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        throw new IllegalArgumentException("no tier '" + id + "' in family " + name);
    }
}
