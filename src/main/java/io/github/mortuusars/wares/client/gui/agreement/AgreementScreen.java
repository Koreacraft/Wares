package io.github.mortuusars.wares.client.gui.agreement;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mortuusars.mpfui.component.HorizontalAlignment;
import io.github.mortuusars.mpfui.component.Rectangle;
import io.github.mortuusars.mpfui.component.TooltipBehavior;
import io.github.mortuusars.mpfui.renderable.TextBlockRenderable;
import io.github.mortuusars.mpfui.renderable.TextureRenderable;
import io.github.mortuusars.wares.Wares;
import io.github.mortuusars.wares.client.gui.agreement.element.StampRenderable;
import io.github.mortuusars.wares.data.Lang;
import io.github.mortuusars.wares.data.agreement.Agreement;
import io.github.mortuusars.wares.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class AgreementScreen extends AbstractContainerScreen<AgreementMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Wares.ID, "textures/gui/agreement.png");
    private static final ResourceLocation STAMPS_TEXTURE = new ResourceLocation(Wares.ID, "textures/gui/stamps.png");
    private static final int FONT_COLOR = 0xff886447;
    private Screen parentScreen;

    public AgreementScreen(AgreementMenu menu) {
        super(menu, menu.playerInventory, TextComponent.EMPTY);
        minecraft = Minecraft.getInstance(); // Minecraft is null if not updated here
    }

    public boolean isOpen() {
        return minecraft != null && minecraft.screen == this;
    }

    public void showAsOverlay() {
        if (minecraft != null) {
            if (!isOpen()) {
                parentScreen = minecraft.screen;
            }
            minecraft.setScreen(this);

            Vec3 pos = menu.player.position();
            menu.level.playSound(menu.player, pos.x, pos.y, pos.z, Wares.SoundEvents.AGREEMENT_CRACKLE.get(), SoundSource.MASTER,
                    1f, menu.level.getRandom().nextFloat() * 0.1f + 0.7f);
        }
    }

    @Override
    public void onClose() {
        if (isOpen() && minecraft != null) {
            minecraft.setScreen(parentScreen);
            parentScreen = null;

            Vec3 pos = menu.player.position();
            menu.level.playSound(menu.player, pos.x, pos.y, pos.z, Wares.SoundEvents.AGREEMENT_CRACKLE.get(), SoundSource.MASTER,
                    1f, menu.level.getRandom().nextFloat() * 0.1f + 1f);

            return;
        }
        super.onClose();
    }

    protected Agreement getAgreement() {
        return menu.getAgreement();
    }

    @Override
    protected void init() {
        this.imageWidth = menu.getUIWidth();
        this.imageHeight = menu.getUIHeight();
        super.init();
        inventoryLabelY = -1000;
        titleLabelY = -1000;

        AgreementLayout layout = menu.getLayout().offset(getGuiLeft(), getGuiTop() + menu.posYOffset);

        // TITLE
        Rectangle titleRect = layout.getElement(AgreementLayout.Element.TITLE);
        if (titleRect != null) { // Extra safety. Title should not be null.
            addRenderableOnly(new TextBlockRenderable(menu.getTitle(), titleRect.left(), titleRect.top(), titleRect.width, titleRect.height)
                    .setAlignment(HorizontalAlignment.CENTER)
                    .setDefaultColor(FONT_COLOR));
        }

        // MESSAGE
        Rectangle messageRect = layout.getElement(AgreementLayout.Element.MESSAGE);
        if (messageRect != null) {
            addRenderableOnly(new TextBlockRenderable(menu.getMessage(), messageRect.left(), messageRect.top(), messageRect.width, messageRect.height)
                    .setAlignment(HorizontalAlignment.CENTER)
                    .setDefaultColor(FONT_COLOR));
        }

        // ARROW
        Rectangle slotsRect = layout.getElement(AgreementLayout.Element.SLOTS);
        if (slotsRect != null) {
            addRenderableOnly(new TextureRenderable(slotsRect.centerX() - 9, slotsRect.centerY() - 6, 19, 11,
                    200, 20, 0, TEXTURE));
        }

        // ORDERS
        Rectangle orderedRect = layout.getElement(AgreementLayout.Element.ORDERED);
        if (orderedRect != null) {
            addRenderableOnly(new TextBlockRenderable(() -> new TextComponent(
                    TextUtil.shortenNumber(getAgreement().getDelivered()) + " / " +
                            TextUtil.shortenNumber(getAgreement().getOrdered())), orderedRect.left(), orderedRect.top(), orderedRect.width, orderedRect.height)
                    .setDefaultColor(FONT_COLOR)
                    .setTooltip(() -> {
                        int delivered = getAgreement().getDelivered();
                        int ordered = getAgreement().getOrdered();
                        if (delivered >= 1000 || ordered >= 1000)
                            return Lang.GUI_AGREEMENT_DELIVERIES_TOOLTIP.translate(delivered, ordered);
                        else return TextComponent.EMPTY;
                    })
                    .setTooltipBehavior(TooltipBehavior.REGULAR_ONLY)
                    .setAlignment(HorizontalAlignment.CENTER));
        }

        // EXPIRY
        Rectangle expiryRect = layout.getElement(AgreementLayout.Element.EXPIRY);
        if (expiryRect != null) {
            addRenderableOnly(new TextBlockRenderable(() -> TextUtil.timeFromTicks(getAgreement().getExpireTime() - menu.level.getGameTime()),
                    expiryRect.left(), expiryRect.top(), expiryRect.width, expiryRect.height)
                    .setAlignment(HorizontalAlignment.CENTER)
                    .setTooltip(Lang.GUI_AGREEMENT_EXPIRE_TIME.translate()))
                    .setTooltipBehavior(TooltipBehavior.REGULAR_ONLY)
                    .setDefaultColor(0xad3232)
                    .visibility((renderable, poseStack, mouseX, mouseY) -> !getAgreement().isCompleted()
                            && getAgreement().getExpireTime() - menu.level.getGameTime() > 0);
        }

        // COMPLETED STAMP
        addRenderableOnly(new StampRenderable(getGuiLeft() + 12, getGuiTop() + 10, 71, 23,
                1, 1, STAMPS_TEXTURE)
                .setTooltip(Lang.GUI_AGREEMENT_COMPLETED.translate()))
                .setOpacity(0.75f)
                .visibility((renderable, poseStack, mouseX, mouseY) -> getAgreement().isCompleted());

        // EXPIRED STAMP
        addRenderableOnly(new StampRenderable(getGuiLeft() + 12, getGuiTop() + 10, 71, 23,
                1, 27, STAMPS_TEXTURE)
                .setTooltip(Lang.GUI_AGREEMENT_EXPIRED.translate()))
                .setOpacity(0.75f)
                .visibility((renderable, poseStack, mouseX, mouseY) -> !getAgreement().isCompleted() && getAgreement().isExpired(menu.level.getGameTime()));

        // SEAL

        MutableComponent buyerInfoTooltip = new TextComponent("");

        if (getAgreement().getBuyerName() != TextComponent.EMPTY)
            buyerInfoTooltip.append(getAgreement().getBuyerName());

        if (getAgreement().getBuyerAddress() != TextComponent.EMPTY) {
            if (getAgreement().getBuyerName() != TextComponent.EMPTY)
                buyerInfoTooltip.append("\n");
            buyerInfoTooltip.append(getAgreement().getBuyerAddress());
        }

        addRenderableOnly(new TextureRenderable(getGuiLeft() + (imageWidth / 2) - (36 / 2), getGuiTop() + imageHeight - 35,
                36, 35, 200, 32, TEXTURE))
                .setTooltip(buyerInfoTooltip);
    }

    @Override
    public void renderBg(@NotNull PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        if (menu.isShort) {
            this.blit(poseStack, this.leftPos, this.topPos, 0, 0, this.imageWidth, 111);
            this.blit(poseStack, this.leftPos, this.topPos + 111, 0, 155, this.imageWidth, 101);
        }
        else
            this.blit(poseStack, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        //Slots BG
        for (Slot slot : menu.slots) {
            this.blit(poseStack, getGuiLeft() + slot.x - 1, getGuiTop() + slot.y - 1, 201, 1, 18, 18);
        }
    }

    @Override
    public void render(@NotNull PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        this.renderTooltip(poseStack, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(@NotNull PoseStack pPoseStack, int pX, int pY) {
        super.renderTooltip(pPoseStack, pX, pY);
    }

    @Override
    protected void renderLabels(@NotNull PoseStack poseStack, int mouseX, int mouseY) {
        super.renderLabels(poseStack, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 1) {
            // TODO: config
            onClose();
            return true;
        }
        else
            return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    protected void slotClicked(@NotNull Slot pSlot, int pSlotId, int pMouseButton, @NotNull ClickType pType) {
        // Ignored
    }
}
