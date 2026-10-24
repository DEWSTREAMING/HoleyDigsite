package com.holeydigsite.client;

import com.holeydigsite.economy.Selling;
import com.holeydigsite.shop.custom.CustomEntry;
import com.holeydigsite.shop.custom.ShopSetupNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Phase 21d: the operator's Shop Display setup screen. An item slot (filled by clicking an item of your inventory below - the
 * item is only COPIED, nothing is used up), a count, a sell price and an optional buy-back price (gold / silver / copper
 * fields with the total in copper), and Save / Clear / Cancel. The server checks everything again on Save.
 */
public class ShopSetupScreen extends Screen {
    private static final int WIN_W = 252;
    private static final int WIN_H = 232;

    private final BlockPos pos;
    private final boolean hadEntry;
    private ItemStack template;
    private boolean buyBackOn;
    private final int startCount;
    private final long startPrice;
    private final long startBuy;

    private int left;
    private int top;
    private EditBox count;
    private EditBox[] sell = new EditBox[3];
    private EditBox[] buy = new EditBox[3];
    private Button buyToggle;
    private Button saveButton;

    public ShopSetupScreen(ShopSetupNetwork.OpenPayload p) {
        super(Component.translatable("screen.holeydigsite.shop_setup.title"));
        this.pos = p.pos();
        this.hadEntry = p.hasEntry();
        this.template = p.template();
        this.startCount = p.count();
        this.startPrice = p.price();
        this.startBuy = p.buyBack();
        this.buyBackOn = p.buyBack() > 0;
    }

    @Override
    protected void init() {
        left = (width - WIN_W) / 2;
        top = (height - WIN_H) / 2;
        count = box(left + 190, top + 22, 40, Integer.toString(startCount));
        long[] sp = Selling.split(startPrice);
        long[] bp = Selling.split(startBuy);
        for (int i = 0; i < 3; i++) {
            // split() returns {gold, silver, copper}
            sell[i] = box(left + 52 + i * 62, top + 52, 36, startPrice > 0 ? Long.toString(sp[i]) : "");
            buy[i] = box(left + 52 + i * 62, top + 92, 36, startBuy > 0 ? Long.toString(bp[i]) : "");
        }
        buyToggle = addRenderableWidget(Button.builder(Component.empty(), b -> {
            buyBackOn = !buyBackOn;
            refresh();
        }).bounds(left + 10, top + 74, 110, 14).build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("screen.holeydigsite.shop_setup.save"), b -> save())
                .bounds(left + 10, top + WIN_H - 26, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.holeydigsite.shop_setup.clear"), b -> {
            PacketDistributor.sendToServer(new ShopSetupNetwork.SavePayload(pos, true, ItemStack.EMPTY, 1, 0, 0));
            onClose();
        }).bounds(left + 90, top + WIN_H - 26, 70, 18).build()).active = hadEntry;
        addRenderableWidget(Button.builder(Component.translatable("screen.holeydigsite.shop_setup.cancel"), b -> onClose())
                .bounds(left + 170, top + WIN_H - 26, 70, 18).build());
        refresh();
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.empty());
        b.setFilter(s -> s.matches("\\d{0,9}"));
        b.setValue(value);
        b.setResponder(s -> refresh());
        addRenderableWidget(b);
        return b;
    }

    private static long value(EditBox box) {
        String s = box.getValue();
        return s.isEmpty() ? 0 : Long.parseLong(s);
    }

    private long sellTotal() {
        return value(sell[0]) * Selling.GOLD + value(sell[1]) * Selling.SILVER + value(sell[2]);
    }

    private long buyTotal() {
        return buyBackOn ? value(buy[0]) * Selling.GOLD + value(buy[1]) * Selling.SILVER + value(buy[2]) : 0;
    }

    private int countValue() {
        String s = count.getValue();
        return s.isEmpty() ? 0 : (int) Math.min(1000, Long.parseLong(s));
    }

    private boolean valid() {
        return !template.isEmpty() && countValue() >= 1 && countValue() <= CustomEntry.MAX_COUNT && sellTotal() >= 1
                && sellTotal() <= CustomEntry.MAX_PRICE && (!buyBackOn || (buyTotal() >= 1 && buyTotal() <= CustomEntry.MAX_PRICE));
    }

    private void refresh() {
        if (buyToggle == null || saveButton == null) return;
        buyToggle.setMessage(Component.translatable(buyBackOn ? "screen.holeydigsite.shop_setup.buyback_on" : "screen.holeydigsite.shop_setup.buyback_off"));
        for (EditBox b : buy) {
            b.visible = buyBackOn;
        }
        saveButton.active = valid();
    }

    private void save() {
        if (!valid()) return;
        PacketDistributor.sendToServer(new ShopSetupNetwork.SavePayload(pos, false, template.copyWithCount(1), countValue(), sellTotal(), buyTotal()));
        onClose();
    }

    /** The label players will see above the display (same wording as the real renderer). */
    private Component preview() {
        if (template.isEmpty()) {
            return Component.translatable("screen.holeydigsite.shop_setup.preview_empty").withStyle(ChatFormatting.GRAY);
        }
        long price = Math.max(0, sellTotal());
        return countValue() > 1
                ? Component.translatable("tooltip.holeydigsite.display.price_count", countValue(), Selling.format(price))
                : Component.translatable("tooltip.holeydigsite.display.price", Selling.format(price));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 10, top + 8, 0xFFF3D27A, true);

        // item slot
        g.fill(left + 10, top + 20, left + 30, top + 40, 0xFF1A140E);
        g.fill(left + 11, top + 21, left + 29, top + 39, 0xFF4A3B2C);
        if (!template.isEmpty()) {
            g.renderItem(template, left + 12, top + 22);
            g.drawString(font, font.plainSubstrByWidth(template.getHoverName().getString(), 130), left + 36, top + 26, 0xFFFFFFFF, false);
        } else {
            g.drawString(font, Component.translatable("screen.holeydigsite.shop_setup.pick"), left + 36, top + 26, 0xFFB0A090, false);
        }
        g.drawString(font, Component.translatable("screen.holeydigsite.shop_setup.count"), left + 160, top + 26, 0xFFD8CFC2, false);

        // prices
        g.drawString(font, Component.translatable("screen.holeydigsite.shop_setup.sell"), left + 10, top + 55, 0xFFD8CFC2, false);
        String[] units = {"g", "s", "c"};
        for (int i = 0; i < 3; i++) {
            g.drawString(font, units[i], left + 52 + i * 62 + 39, top + 55, 0xFF9E9489, false);
            if (buyBackOn) {
                g.drawString(font, units[i], left + 52 + i * 62 + 39, top + 95, 0xFF9E9489, false);
            }
        }
        g.drawString(font, "= " + sellTotal() + " copper", left + 10, top + 68, 0xFFFFD35B, false);
        if (buyBackOn) {
            g.drawString(font, Component.translatable("screen.holeydigsite.shop_setup.buy"), left + 10, top + 95, 0xFFD8CFC2, false);
            g.drawString(font, "= " + buyTotal() + " copper", left + 130, top + 78, 0xFFFFD35B, false);
        }
        g.drawString(font, Component.translatable("screen.holeydigsite.shop_setup.preview"), left + 10, top + 114, 0xFF9E9489, false);
        g.drawString(font, preview(), left + 60, top + 114, 0xFFFFFFFF, false);

        // inventory picker (copies the clicked item)
        g.drawString(font, Component.translatable("screen.holeydigsite.shop_setup.inventory"), left + 10, top + 128, 0xFF9E9489, false);
        Player player = Minecraft.getInstance().player;
        ItemStack hovered = ItemStack.EMPTY;
        if (player != null) {
            for (int i = 0; i < 36; i++) {
                int sx = slotX(i), sy = slotY(i);
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF2D2218);
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty()) {
                    g.renderItem(s, sx + 1, sy + 1);
                    g.renderItemDecorations(font, s, sx + 1, sy + 1);
                }
                if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                    g.fill(sx, sy, sx + 18, sy + 18, 0x40FFFFFF);
                    hovered = s;
                }
            }
        }
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    /** Slot i of the player inventory: hotbar (0-8) at the bottom, main rows above. */
    private int slotX(int i) {
        return left + 45 + (i % 9) * 18;
    }

    private int slotY(int i) {
        int row = i < 9 ? 3 : (i - 9) / 9;
        return top + 140 + row * 18;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        Player player = Minecraft.getInstance().player;
        if (button == 0 && player != null) {
            for (int i = 0; i < 36; i++) {
                if (mx >= slotX(i) && mx < slotX(i) + 18 && my >= slotY(i) && my < slotY(i) + 18) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (!s.isEmpty()) {
                        template = s.copyWithCount(1);
                        refresh();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        g.fill(left - 2, top - 2, left + WIN_W + 2, top + WIN_H + 2, 0xFF140F0A);
        g.fill(left, top, left + WIN_W, top + WIN_H, 0xFF3B2E22);
        g.fill(left + 2, top + 2, left + WIN_W - 2, top + WIN_H - 2, 0xFF2D2218);
    }
}
