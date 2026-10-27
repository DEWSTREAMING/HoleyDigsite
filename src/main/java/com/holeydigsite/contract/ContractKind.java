package com.holeydigsite.contract;

import java.util.Locale;

/** What a contract asks for. */
public enum ContractKind {
    METAL, GEM, FOOD, PIECE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ContractKind byId(String id) {
        for (ContractKind k : values()) {
            if (k.id().equals(id)) {
                return k;
            }
        }
        throw new IllegalArgumentException("Unknown contract kind: " + id);
    }
}
