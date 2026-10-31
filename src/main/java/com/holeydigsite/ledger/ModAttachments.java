package com.holeydigsite.ledger;

import com.holeydigsite.HoleyDigsite;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers the per-player {@link LedgerData} attachment type.
 */
public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HoleyDigsite.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LedgerData>> LEDGER_DATA =
            ATTACHMENT_TYPES.register("ledger_data", () ->
                    AttachmentType.serializable(LedgerData::new)
                            .copyOnDeath()
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<com.holeydigsite.event.OpalChips>> OPAL_CHIPS =
            ATTACHMENT_TYPES.register("opal_chips", () ->
                    AttachmentType.serializable(com.holeydigsite.event.OpalChips::new)
                            .copyOnDeath()
                            .build()
            );
}
