// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.libworks.machine.client;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.Ghosts;
import io.github._5thlayer.libworks.ghost.client.GhostDraw;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The one stand-in look of a machine screen for the whole suite, drawn from fills until there is art: the grey
 * panel, its slots' recesses, bars, fluid gauges and their tooltips, and text cut to fit. Client-only: nothing in
 * common code may reference it.
 *
 * <p>Every position is in screen coordinates, so draw in a pass that is too, such as {@code extractBackground},
 * not in {@code extractLabels}, which is translated to the panel's corner.
 */
public final class MachineDraw {

    public static final int PANEL = 0xFFC6C6C6;
    public static final int SLOT = 0xFF8B8B8B;
    public static final int SLOT_DARK = 0xFF373737;
    public static final int SLOT_LIGHT = 0xFFFFFFFF;
    /** Labels drawn on the panel, such as the status line. */
    public static final int TEXT = 0xFF404040;

    public static final int SLOT_SIZE = 16;
    /** A bar's or a gauge's height, and the least a bar's label is drawn on. */
    public static final int BAR_HEIGHT = 12;
    public static final int FLUID_TILE = 16;
    /** The panel's margin, where its labels start. */
    public static final int MARGIN = 8;

    private static final int LABEL = 0xFFFFFFFF;
    /** What a label leaves free of its bar's width. */
    private static final int PADDING = 4;
    private static final int LABEL_INSET = 2;
    private static final FormattedText ELLIPSIS = FormattedText.of("…");

    private MachineDraw() {
    }

    /**
     * The panel at ({@code left}, {@code top}), a recess under each active slot, and the ghosts shown at
     * {@code millis} over them, which the slots' real stacks are drawn over after. Pass the same {@code millis} to
     * {@code ghosts.at} for the tooltip, so it names the ghost drawn. The screen calls its
     * {@code super.extractBackground} first, which dims the world behind.
     */
    public static void panel(GuiGraphicsExtractor graphics, Font font, int left, int top, int width, int height,
            Iterable<Slot> slots, Ghosts ghosts, long millis) {
        graphics.fill(left, top, left + width, top + height, PANEL);
        for (Slot slot : slots) {
            if (slot.isActive()) {
                recess(graphics, left + slot.x, top + slot.y, SLOT_SIZE, SLOT_SIZE);
            }
        }
        for (Ghost ghost : ghosts.shown(millis)) {
            GhostDraw.draw(graphics, font, ghost);
        }
    }

    /** A slot's bevel: dark above and left, light below and right, grey inside. */
    public static void recess(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width, y, SLOT_DARK);
        graphics.fill(x - 1, y, x, y + height, SLOT_DARK);
        graphics.fill(x, y + height, x + width + 1, y + height + 1, SLOT_LIGHT);
        graphics.fill(x + width, y, x + width + 1, y + height, SLOT_LIGHT);
        graphics.fill(x, y, x + width, y + height, SLOT);
    }

    /**
     * A bar in its recess, {@code fraction} of it filled with {@code colour}, and {@code label} centred over it when
     * it fits the width and the bar is at least {@link #BAR_HEIGHT} tall. An empty label draws nothing.
     */
    public static void bar(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height, float fraction,
            int colour, Component label) {
        recess(graphics, x, y, width, height);
        graphics.fill(x, y, x + Math.round(width * Math.clamp(fraction, 0f, 1f)), y + height, colour);
        if (height >= BAR_HEIGHT && font.width(label) <= width - PADDING) {
            graphics.text(font, label, x + (width - font.width(label)) / 2, y + (height - font.lineHeight) / 2 + 1, LABEL, true);
        }
    }

    /**
     * A fluid gauge in its recess: the fluid's still texture, tinted as it is in the world, tiled over the part of
     * the gauge the fluid fills, with the amount in mB written over it when a full gauge's would fit, so a narrow
     * gauge never shows one. An empty stack draws only the recess.
     */
    public static void gauge(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, FluidStack fluid, int capacity) {
        recess(graphics, x, y, width, BAR_HEIGHT);
        if (fluid.isEmpty()) {
            return;
        }
        var state = fluid.getFluid().defaultFluidState();
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(state);
        int tint = model.fluidTintSource() == null ? 0xFFFFFFFF : 0xFF000000 | model.fluidTintSource().color(state);
        int filled = Math.min(width, Math.round(width * (float) fluid.getAmount() / Math.max(1, capacity)));
        graphics.enableScissor(x, y, x + filled, y + BAR_HEIGHT);
        for (int tileX = x; tileX < x + filled; tileX += FLUID_TILE) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.stillMaterial().sprite(), tileX, y, FLUID_TILE, FLUID_TILE, tint);
        }
        graphics.disableScissor();
        // Judged by a full gauge's label, so a gauge shows its amount at every fill or at none; a tank left above its
        // capacity shows it only while that fits too.
        Component amount = millibuckets(fluid.getAmount());
        if (Math.max(font.width(millibuckets(capacity)), font.width(amount)) <= width - PADDING) {
            graphics.text(font, amount, x + LABEL_INSET, y + LABEL_INSET, LABEL, true);
        }
    }

    private static Component millibuckets(int amount) {
        return Component.literal(NumberFormat.getIntegerInstance().format(amount) + " mB");
    }

    /** Over a gauge, the fluid's name, or that it is empty, and how much of the tank it fills. */
    public static void fluidTooltip(GuiGraphicsExtractor graphics, Font font, FluidStack fluid, int capacity, int mouseX, int mouseY) {
        graphics.setComponentTooltipForNextFrame(font, fluidLines(fluid, capacity), mouseX, mouseY);
    }

    /**
     * Over a named tank's gauge, its {@code title}, such as "Water", then the fluid's name, or that it is empty, and
     * how much of the tank it fills.
     */
    public static void fluidTooltip(GuiGraphicsExtractor graphics, Font font, Component title, FluidStack fluid, int capacity,
            int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(title);
        lines.addAll(fluidLines(fluid, capacity));
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private static List<Component> fluidLines(FluidStack fluid, int capacity) {
        NumberFormat number = NumberFormat.getIntegerInstance();
        return List.of(
                fluid.isEmpty() ? Component.translatable("libworks.machine.fluid_empty").withStyle(ChatFormatting.GRAY) : fluid.getHoverName(),
                Component.translatable("libworks.machine.fluid", number.format(fluid.getAmount()), number.format(capacity))
                        .withStyle(ChatFormatting.GRAY));
    }

    /** Whether ({@code mouseX}, {@code mouseY}) is over the box at ({@code x}, {@code y}), {@code width} by {@code height}. */
    public static boolean over(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** {@code line}, cut to {@code width} with an ellipsis when it is wider. */
    public static FormattedCharSequence fitted(Font font, Component line, int width) {
        if (font.width(line) <= width) {
            return line.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(line, width - font.width(ELLIPSIS));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, ELLIPSIS));
    }

    /** The whole of {@code line} as a tooltip, when {@link #fitted} cut it to {@code width}. */
    public static void wholeOnHover(GuiGraphicsExtractor graphics, Font font, Component line, int width, int mouseX, int mouseY) {
        if (font.width(line) > width) {
            graphics.setComponentTooltipForNextFrame(font, List.of(line), mouseX, mouseY);
        }
    }
}
