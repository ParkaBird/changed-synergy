package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.Color3;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation.Approach;
import net.parkabird.changedsynergy.client.RelationshipPanel.InfoCard;
import net.parkabird.changedsynergy.client.RelationshipPanel.PreparedCard;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu.CommunityStorage;
import net.parkabird.changedsynergy.world.inventory.SocialInteractionMenu;

/** Social wheel using Changed's native radial geometry and faction colors. */
public final class SocialInteractionScreen
        extends AbstractRadialScreen<SocialInteractionMenu> {
    private static final List<Action> RELATIONSHIP_ACTIONS = List.of(
            emote("social_talk", "pause"),
            emote("social_pat", "heart"),
            synergyIcon("social_gift", "orange_gray.png", 16),
            emote("social_play", "idea"),
            changedIcon("social_follow",
                    "textures/gui/tamed_dl_interactions/cycle_follow.png", 16));
    private static final Action VOLUNTARY_TRANSFUR = new Action(
            "social_voluntary_transfur",
            Changed.modResource(
                    "textures/abilities/switch_transfur_mode_replication.png"),
            32);
    private static final Action NEGOTIATION_ENTRY =
            emote("social_negotiation", "pause");
    private static final Action NATIVE_BOND = emote("social_native_bond", "heart");
    private static final Action FRIENDLY_TRANSFUR = new Action("social_friendly_transfur", VOLUNTARY_TRANSFUR.texture(), 32);
    private static final Action WITHDRAW_TOKEN = emote("social_withdraw_token", "deny");
    private static final Action PROVISIONER_TRADE = synergyIcon(
            "social_trade", "cooked_fish_gray.png", 16);
    private static final List<Action> BONDED_ACTIONS = List.of(
            emote("social_talk", "pause"),
            emote("social_pat", "heart"),
            synergyIcon("social_gift", "orange_gray.png", 16),
            emote("social_play", "idea"),
            emote("social_rest", "sleepy"));
    private static final List<Action> PLAYER_ACTIONS = List.of(
            emote("social_talk", "pause"), emote("social_pat", "heart"),
            synergyIcon("social_gift", "orange_gray.png", 16),
            emote("social_play", "idea"),
            changedIcon("social_follow", "textures/gui/tamed_dl_interactions/cycle_follow.png", 16));
    private static final List<Action> NEGOTIATION_ACTIONS = List.of(
            emote("negotiation_reason", "pause"),
            emote("negotiation_empathy", "heart"),
            emote("negotiation_apology", "confused"),
            emote("negotiation_bargain", "idea"),
            emote("negotiation_insist", "deny"),
            emote("negotiation_forgive", "heart"));
    private static final Action NEGOTIATION_FOOD_BRIBE = synergyIcon(
            "negotiation_food_bribe", "orange_gray.png", 16);
    private static final Action NEGOTIATION_FOOD_BRIBE_FELINE = synergyIcon(
            "negotiation_food_bribe", "cooked_fish_gray.png", 16);
    private final List<Action> actions;
    @Nullable
    private final LivingEntity absorptionPreviewEntity;
    private static List<Action> npcActions(List<Action> entries,SocialInteractionMenu menu) {
        boolean fixed=menu.getProgression().getBoolean("StoryNpc");
        boolean chief=menu.getRole() == net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole.CHIEF;
        if(!fixed&&!menu.getProgression().getBoolean("CampDialogue")&&!chief)return List.copyOf(entries);
        return entries.stream()
                .filter(a->!chief||!a.command().equals("social_play"))
                .filter(a->!fixed||!java.util.Set.of("social_follow","social_native_bond","social_voluntary_transfur","social_friendly_transfur","social_inventory").contains(a.command()))
                .filter(a->!menu.getProgression().getBoolean("NoNpcDialogue")||!a.command().equals("social_talk"))
                .map(a->a.command().equals("social_talk")?new Action("npc_dialogue",a.texture(),a.textureSize()):a).toList();
    }
    private boolean manualGoodbyeSent;
    private boolean voluntaryConfirmationArmed;

    private final long panelsOpenedAt = System.nanoTime();

    public SocialInteractionScreen(
            SocialInteractionMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title, colors(menu, inventory).background(),
                colors(menu, inventory).foreground(), center(menu, inventory));
        this.absorptionPreviewEntity = createAbsorptionPreview(menu);
        if (menu.isPlayerMode()) {
            List<Action> entries=new ArrayList<>(PLAYER_ACTIONS);
            if(menu.getProgression().getBoolean("PlayerEligible"))entries.add(emote(menu.getProgression().getBoolean("PlayerFriends")?"social_unfriend":"social_friend","heart"));
            actions=npcActions(entries,menu);
        } else if (menu.isNegotiationMode()) {
            List<Action> entries = new ArrayList<>(NEGOTIATION_ACTIONS);
            if (menu.isFoodBribeAvailable()) {
                entries.add(menu.isFelineFoodBribe()
                        ? NEGOTIATION_FOOD_BRIBE_FELINE
                        : NEGOTIATION_FOOD_BRIBE);
            }
            actions = npcActions(entries,menu);
        } else if (menu.isBondedMode()) {
            List<Action> entries = new ArrayList<>(BONDED_ACTIONS);
            if (menu.getProgression().getBoolean("CanFriendlyTransfur")) entries.add(FRIENDLY_TRANSFUR);
            if (menu.isProvisionerTradeAvailable()) {
                entries.add(PROVISIONER_TRADE);
            }
            if (menu.getProgression().getBoolean("TokenReserved")) entries.add(WITHDRAW_TOKEN);
            addSeatAction(entries, menu);
            actions = npcActions(entries,menu);
        } else {
            List<Action> entries = new ArrayList<>();
            if (menu.hasTrustedRelationship()) {
                entries.addAll(RELATIONSHIP_ACTIONS);
            } else if (menu.getProgression().getBoolean("BasicWheel")) {
                entries.addAll(RELATIONSHIP_ACTIONS.subList(0, 3));
            }
            if (menu.isVoluntaryBondAvailable()) {
                entries.add(VOLUNTARY_TRANSFUR);
            }
            if (menu.isNativeBondAvailable()) {
                entries.add(NATIVE_BOND);
            }
            if (menu.getProgression().getBoolean("CanFriendlyTransfur")) entries.add(FRIENDLY_TRANSFUR);
            if (menu.getProgression().getBoolean("TokenReserved")) entries.add(WITHDRAW_TOKEN);
            if (menu.canFriendInventory()) entries.add(changedIcon("social_inventory",
                    "textures/gui/tamed_dl_interactions/view_inventory.png", 16));
            if (menu.isNegotiationAvailable()) {
                entries.add(NEGOTIATION_ENTRY);
            }
            if (menu.isProvisionerTradeAvailable()) {
                entries.add(PROVISIONER_TRADE);
            }
            addSeatAction(entries, menu);
            actions = npcActions(entries,menu);
        }
    }

    private static void addSeatAction(List<Action> entries, SocialInteractionMenu menu) {
        String command = menu.getProgression().getString("SeatAction");
        if (!command.isEmpty()) {
            var action = new Action(command, CompanionSeatIcons.id(command.equals("social_seat")), CompanionSeatIcons.SIZE);
            if (command.equals("social_seat")) entries.add(action);
            else entries.add(0, action);
        }
    }

    private static LivingEntity center(
            SocialInteractionMenu menu,
            Inventory inventory) {
        if (menu.isPlayerMode() && menu.getSocialPlayerBody() != null) return menu.getSocialPlayerBody();
        return menu.getCreature() != null ? menu.getCreature() : inventory.player;
    }

    @Nullable
    private static LivingEntity createAbsorptionPreview(
            SocialInteractionMenu menu) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!menu.isVirtualAbsorptionNegotiation()
                || !menu.isWhiteKnightSplitNegotiation()
                || minecraft.level == null) {
            return null;
        }
        var type = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.fromNamespaceAndPath(
                        "changed", "white_latex_knight"));
        Entity created = type == null ? null : type.create(minecraft.level);
        if (!(created instanceof ChangedEntity knight)) {
            return null;
        }
        CompoundTag appearance = menu.getNegotiationAppearance();
        if (!appearance.isEmpty()) {
            knight.getBasicPlayerInfo().load(appearance);
        }
        return knight;
    }

    @Nullable
    public LivingEntity getAbsorptionPreviewEntity() {
        return absorptionPreviewEntity;
    }

    private static Action emote(String command, String name) {
        return synergyIcon(command, "emote/" + name + "_gray.png", 32);
    }

    private static Action synergyIcon(
            String command,
            String path,
            int size) {
        return new Action(
                command,
                ResourceLocation.fromNamespaceAndPath(
                        ChangedSynergyMod.MOD_ID,
                        "textures/gui/radial/icons/" + path),
                size);
    }

    private static Action changedIcon(
            String command,
            String path,
            int size) {
        return new Action(command, Changed.modResource(path), size);
    }

    private static ColorScheme colors(
            SocialInteractionMenu menu,
            Inventory inventory) {
        ChangedEntity creature = menu.getCreature();
        if (menu.isPlayerMode()) {
            LivingEntity body = menu.getSocialPlayerBody();
            if (body instanceof ChangedEntity proxy) creature = proxy;
            else if (body instanceof net.minecraft.world.entity.player.Player target) {
                var variant = ProcessTransfur.getPlayerTransfurVariant(target);
                if (variant != null) return readableColors(AbstractRadialScreen.getColors(variant));
            }
        }
        if (menu.isVirtualAbsorptionNegotiation()) {
            var variant = ProcessTransfur.getPlayerTransfurVariant(
                    inventory.player);
            if (variant != null) {
                return readableColors(AbstractRadialScreen.getColors(variant));
            }
        }
        if (creature == null || creature.getSelfVariant() == null) {
            return new ColorScheme(Color3.GRAY, Color3.WHITE).setForegroundToBright();
        }
        var pair = creature.getSelfVariant().getColors();
        return readableColors(new ColorScheme(
                pair.getFirst(), pair.getSecond()).setForegroundToBright());
    }

    private static ColorScheme readableColors(ColorScheme source) {
        Color3 background = source.background();
        Color3 foreground = source.foreground();
        if (background.brightness() < 0.44F
                && foreground.brightness() < 0.64F) {
            float amount = (0.68F - foreground.brightness())
                    / Math.max(0.001F, 1.0F - foreground.brightness());
            foreground = foreground.lerp(
                    Math.min(0.84F, Math.max(0.0F, amount)),
                    Color3.WHITE).clamp();
        }
        return new ColorScheme(background, foreground);
    }

    @Override
    public int getCount() {
        return actions.size();
    }

    @Override
    public Optional<Integer> getSectionAt(int mouseX, int mouseY) {
        int wheelOffset = Math.round(
                RadialWheelAnimations.horizontalOffset(this));
        return super.getSectionAt(mouseX - wheelOffset, mouseY);
    }

    @Nullable
    @Override
    public List<Component> tooltipsFor(int section) {
        if (section < 0 || section >= actions.size()) {
            return null;
        }
        Action action = actions.get(section);
        if (menu.isPlayerMode()) {
            String hint = "social_follow".equals(action.command())
                    ? menu.isFollowing() ? "stop_hint"
                    : menu.isInstinctPlayer() ? "instinct_hint" : "invite_hint"
                    : action.command() + "_hint";
            return List.of(Component.translatable("menu.changed_synergy.player_social." + action.command()),
                    Component.translatable("menu.changed_synergy.player_social." + hint));
        }
        if ("social_follow".equals(action.command())) {
            if (!menu.canFriendFollow()) {
                return List.of(
                        Component.translatable(
                                "menu.changed_synergy.social.social_follow"),
                        Component.translatable(
                                "menu.changed_synergy.social.follow_locked"));
            }
            return List.of(
                    Component.translatable("menu.changed_synergy.social.social_follow"),
                    Component.translatable(menu.isFollowing()
                            ? "menu.changed_synergy.social.wait"
                            : "menu.changed_synergy.social.walk"));
        }
        if ("social_gift".equals(action.command())) {
            List<Component> hints = new ArrayList<>();
            hints.add(Component.translatable("menu.changed_synergy.social.social_gift"));
            hints.add(Component.translatable("menu.changed_synergy.social.social_gift_hint"));
            if (!menu.isPlayerMode()) {
                if (menu.giftWaitSeconds() > 0) hints.add(Component.translatable("message.changed_synergy.relationship.gift_cooldown", menu.giftWaitSeconds()));
                if (!menu.hasTrustedRelationship() && menu.getProgression().getBoolean("Balanced"))
                    hints.add(Component.translatable("message.changed_synergy.relationship.first_gift_requires_care", menu.getProgression().getInt("StrangerLimit")));
                if (menu.getProgression().getBoolean("TokenRequired") && !menu.isBondedMode()) {
                    var token = net.minecraft.world.item.ItemStack.of(menu.getProgression().getCompound("Token"));
                    hints.add(Component.translatable("menu.changed_synergy.social.bond_token_hint", token.getCount(), token.getHoverName()));
                    if (menu.getProgression().getBoolean("TokenReserved")) hints.add(Component.translatable("menu.changed_synergy.social.bond_token_reserved"));
                    else if (menu.getProgression().getBoolean("Recognized")) hints.add(Component.translatable("menu.changed_synergy.social.bond_recognized"));
                    else if (menu.getProgression().getBoolean("ReservedElsewhere")) hints.add(Component.translatable("message.changed_synergy.relationship.reserved_elsewhere"));
                }
            }
            return hints;
        }
        if ("social_friendly_transfur".equals(action.command())) return List.of(
                Component.translatable("menu.changed_synergy.social.social_friendly_transfur"),
                Component.translatable("menu.changed_synergy.social.social_friendly_transfur_hint"));
        if ("social_withdraw_token".equals(action.command())) return List.of(
                Component.translatable("menu.changed_synergy.social.social_withdraw_token"),
                Component.translatable("menu.changed_synergy.social.social_withdraw_token_hint"));
        if ("social_voluntary_transfur".equals(action.command())) {
            return List.of(
                    Component.translatable(
                            "menu.changed_synergy.social.social_voluntary_transfur"),
                    Component.translatable(voluntaryConfirmationArmed
                            ? "menu.changed_synergy.social.social_voluntary_transfur_confirm"
                            : "menu.changed_synergy.social.social_voluntary_transfur_hint"));
        }
        if ("social_native_bond".equals(action.command())) {
            return List.of(Component.translatable("menu.changed_synergy.social.social_native_bond"),
                    Component.translatable("menu.changed_synergy.social.social_native_bond_hint"));
        }
        if ("social_negotiation".equals(action.command())) {
            return List.of(
                    Component.translatable(
                            "menu.changed_synergy.social.social_negotiation"),
                    Component.translatable(
                            "menu.changed_synergy.social.social_negotiation_hint"));
        }
        if ("social_trade".equals(action.command())) {
            return List.of(
                    Component.translatable(
                            "menu.changed_synergy.social.social_trade"),
                    Component.translatable(
                            "menu.changed_synergy.social.social_trade_hint"));
        }
        if (menu.isNegotiationMode()) {
            String key = "menu.changed_synergy.social." + action.command();
            Approach approach = Approach.fromCommand(action.command())
                    .orElse(null);
            if (approach != null
                    && menu.isNegotiationApproachUsed(approach)) {
                return List.of(
                        Component.translatable(key),
                        Component.translatable(
                                "menu.changed_synergy.negotiation.option_used"));
            }
            String hintKey = approach == Approach.FOOD_BRIBE
                    && menu.isFelineFoodBribe()
                            ? key + ".hint.feline" : key + ".hint";
            return List.of(Component.translatable(key),
                    Component.translatable(hintKey));
        }
        return List.of(Component.translatable(
                "menu.changed_synergy.social." + action.command()));
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
        float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRelationshipCards(graphics);
        if (menu.isBondedMode() && !menu.isNegotiationMode()) {
            renderSwitchHint(
                    graphics,
                    RadialWheelAnimations.scrollHint(this,"menu.changed_synergy.wheel.switch_to_functions"));
        }
    }

    private void renderRelationshipCards(GuiGraphics graphics) {
        ChangedEntity creature = menu.getCreature();
        float alpha = RadialWheelAnimations.panelAlpha(this);
        if (alpha <= 0.01F) {
            return;
        }
        if (menu.isPlayerMode()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("menu.changed_synergy.player_social.account", menu.getSocialPlayerAccount()));
            if(menu.getProgression().getBoolean("PlayerEligible"))lines.add(Component.translatable("menu.changed_synergy.player_social.familiarity",menu.getProgression().getInt("PlayerFamiliarity")));
            lines.add(Component.translatable(menu.isInstinctPlayer()
                    ? "menu.changed_synergy.player_social.mode_instinct" : "menu.changed_synergy.player_social.mode_player"));
            LivingEntity body = menu.getSocialPlayerBody();
            ChangedEntity form = body instanceof ChangedEntity proxy ? proxy : null;
            if (body instanceof net.minecraft.world.entity.player.Player target) {
                var variant = ProcessTransfur.getPlayerTransfurVariant(target);
                if (variant != null) form = variant.getChangedEntity();
            }
            if (form != null) {
                HunterFaction faction = HunterFaction.of(form);
                lines.add(form.getType().getDescription());
                lines.add(Component.translatable("menu.changed_synergy.player_social.faction",
                        Component.translatable(faction.translationKey())
                                .withStyle(style -> style.withColor(UiMotion.factionColor(faction)))));
            }
            renderCards(graphics, List.of(new InfoCard(menu.getSocialPlayerName(), lines)), alpha);
            return;
        }

        if (menu.isNegotiationMode()) {
            renderNegotiationCards(
                    graphics,
                    creature == null
                            ? menu.getNegotiationSpeakerName()
                            : creature.getDisplayName(),
                    alpha);
            return;
        }
        if (creature == null) {
            return;
        }

        List<Component> relationship = new ArrayList<>();
        relationship.add(menu.isBondedMode()
                ? Component.translatable(
                        "menu.changed_synergy.social.info.bond",
                        menu.getFamiliarity())
                : Component.translatable(
                        "menu.changed_synergy.social.info.relationship",
                        Component.translatable(menu.getTier().translationKey()),
                        menu.getFamiliarity()));
        relationship.add(Component.translatable(
                "menu.changed_synergy.social.info.trait",
                Component.translatable(
                        "personality.changed_synergy."
                                + menu.getDominantTrait().dialogueKey())));
        var behavior = menu.getBehaviorTrait();
        if (behavior != null && behavior != menu.getDominantTrait()) {
            relationship.add(Component.translatable("menu.changed_synergy.social.info.behavior",
                    Component.translatable("personality.changed_synergy." + behavior.dialogueKey())));
        }
        if (!menu.isBondedMode()) {
            relationship.add(Component.translatable(
                    "menu.changed_synergy.social.info.intent",
                    Component.translatable(
                            "intent.changed_synergy."
                                    + menu.getHumanIntent().dialogueKey())));
        }

        List<Component> currentState = new ArrayList<>();
        currentState.add(Component.translatable(
                "menu.changed_synergy.social.info.health",
                formatHealth(creature.getHealth()),
                formatHealth(creature.getMaxHealth())));
        currentState.add(Component.translatable(
                menu.isLifeEnabled()
                        ? "menu.changed_synergy.social.info.life"
                        : "menu.changed_synergy.social.info.life_paused",
                Component.translatable(
                        "routine.changed_synergy.role." + menu.getRole().id()),
                Component.translatable(
                        "routine.changed_synergy.state." + menu.getRoutine().id())));
        for (int index = 0; menu.getRole() != net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole.WANDERER && menu.getRole() != net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole.CHIEF && index < 3; index++) {
            currentState.add(Component.translatable(
                    "menu.changed_synergy.social.info.role_stat",
                    Component.translatable(
                            "routine.changed_synergy.stat."
                                    + menu.getRole().id() + "." + index),
                    menu.getRoleStat(index)));
        }

        List<Component> connections = new ArrayList<>();
        connections.add(Component.translatable(
                "menu.changed_synergy.social.info.faction",
                Component.translatable(menu.getProgression().getBoolean("LocalCamp")?"camp.changed_synergy.local_trust":
                        menu.getReputationGroupTranslationKey()).withStyle(style -> style.withColor(UiMotion.factionColor(menu.getFaction()))),
                Component.translatable(menu.getFactionStanding().translationKey()),
                menu.getReputation()));
        connections.add(Component.translatable(
                "menu.changed_synergy.social.info.memories",
                menu.getEncounters(), menu.getPats(), menu.getPlays()));
        if (menu.hasCommunity()) {
            connections.add(Component.translatable(
                    "menu.changed_synergy.social.info.community",
                    menu.getStoredItems(),
                    menu.getFoodDelivered(),
                    menu.getMaterialsDelivered(),
                    Component.translatable(storageKey(menu.getCommunityStorage()))));
        }

        List<InfoCard> cards = List.of(
                new InfoCard(creature.getDisplayName(), relationship),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.social.info.section_now"),
                        currentState),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.social.info.section_connections"),
                        connections));

        renderCards(graphics, cards, alpha);
    }

    private void renderNegotiationCards(
            GuiGraphics graphics,
            Component speakerName,
            float alpha) {
        List<Component> circumstances = new ArrayList<>();
        circumstances.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.method",
                Component.translatable(
                        menu.getNegotiationMode().displayTranslationKey(
                                menu.getNegotiationReason()))));
        circumstances.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.reason",
                Component.translatable(
                        menu.getNegotiationReason().translationKey())));
        circumstances.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.trait",
                Component.translatable(
                        "personality.changed_synergy."
                                + menu.getDominantTrait().dialogueKey())));

        List<Component> progress = new ArrayList<>();
        progress.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.progress",
                menu.getNegotiationPercent()));
        progress.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.difficulty",
                Component.translatable(menu.getNegotiationDifficultyKey())));
        progress.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.remaining",
                menu.getNegotiationRemainingApproaches()));

        List<Component> guidance = new ArrayList<>();
        guidance.add(Component.translatable(
                "menu.changed_synergy.negotiation.advice."
                        + menu.getNegotiationReason().name()
                                .toLowerCase(Locale.ROOT)));
        guidance.add(Component.translatable(
                "menu.changed_synergy.negotiation.info.single_use_hint"));
        if (net.parkabird.changedsynergy.ChangedSynergyClientConfig.CLIENT
                .negotiationNextStepHint.get()) {
            Approach next = menu.getSuggestedNegotiationApproach();
            guidance.add(Component.translatable(
                    "menu.changed_synergy.negotiation.info.next",
                    Component.translatable(
                            "menu.changed_synergy.social.negotiation_"
                                    + next.name().toLowerCase(Locale.ROOT))));
        }

        List<InfoCard> cards = List.of(
                new InfoCard(speakerName, circumstances),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.negotiation.info.section_progress"),
                        progress),
                new InfoCard(Component.translatable(
                        "menu.changed_synergy.negotiation.info.section_advice"),
                        guidance));
        renderCards(graphics, cards, alpha);
    }

    private void renderCards(
            GuiGraphics graphics,
            List<InfoCard> cards,
            float alpha) {
        int x = 14;
        int wheelLeft = Math.round(width * 0.5F
                + RadialWheelAnimations.layoutOffset(width) - 128.0F);
        int availableWidth = Math.max(1, wheelLeft - x - 12);
        int cardWidth = RelationshipPanel.CARD_WIDTH;
        int textWidth = cardWidth - 26;
        List<PreparedCard> prepared = cards.stream()
                .map(card -> RelationshipPanel.prepareCard(font, card, textWidth))
                .toList();
        int gap = 10;
        int totalHeight = prepared.stream()
                .mapToInt(PreparedCard::height)
                .sum() + gap * (prepared.size() - 1);
        float panelScale = Math.min(
                1.0F,
                Math.max(0.01F, Math.min((height - 56.0F) / totalHeight,
                        availableWidth / (float)cardWidth)));
        int scaledHeight = Math.round(totalHeight * panelScale);
        int y = Math.max(10, (height - scaledHeight) / 2);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(panelScale, panelScale, 1.0F);
        int cardY = 0;
        for (int index = 0; index < prepared.size(); index++) {
            PreparedCard card = prepared.get(index);
            float entrance = RadialWheelAnimations.instantArrival(this) ? 1 : UiMotion.entrance(panelsOpenedAt, index);
            int slide = UiMotion.reduced() ? 0 : Math.round(-12 * (1 - entrance));
            var style = switch (index) {
                case 0 -> RelationshipPanel.CardStyle.STATUS;
                case 1 -> RelationshipPanel.CardStyle.CONTACTS;
                default -> RelationshipPanel.CardStyle.LOCATION;
            };
            renderInfoCard(graphics, card, slide, cardY, cardWidth,
                    alpha * UiSpring.clamp(entrance), style);
            cardY += card.height() + gap;
        }
        graphics.pose().popPose();
    }

    private void renderInfoCard(
            GuiGraphics graphics,
            PreparedCard card,
            int x,
            int y,
            int cardWidth,
            float alpha,
            RelationshipPanel.CardStyle style) {
        if (alpha <= 0.02F) return;
        int foreground = panelTitleColor();
        int background = panelBackground();
        int textAlpha = Math.round(0xFF * alpha);
        RelationshipPanel.drawCard(graphics, x, y, style, menu.isOrganic(),
                card.height(), background, foreground, alpha);
        graphics.drawString(font, font.plainSubstrByWidth(card.title().getString(), cardWidth - 34),
                x + 25, y + 7, withAlpha(foreground, textAlpha), false);
        int bodyForeground = menu.isOrganic() ? 0 : RelationshipPanel.bodyTextColor(background);
        int lineY = y + RelationshipPanel.GEL_TOP;
        for (FormattedCharSequence line : card.lines()) {
            graphics.drawString(font, line, x + 13, lineY,
                    withAlpha(bodyForeground, textAlpha), false);
            lineY += 9;
        }
    }

    private int panelBackground() {
        return menu.isOrganic() ? 0xC6C6C6
                : colors(menu, minecraft.player.getInventory()).background().toInt();
    }

    private int panelTitleColor() {
        if (menu.isOrganic()) return 0x404040;
        return Color3.fromInt(panelBackground()).brightness() > 0.5F ? 0x222222
                : colors(menu, minecraft.player.getInventory()).foreground().toInt();
    }

    private static String storageKey(CommunityStorage storage) {
        return "menu.changed_synergy.social.info.storage."
                + storage.name().toLowerCase(Locale.ROOT);
    }

    private static String formatHealth(float health) {
        return String.format(Locale.ROOT, "%.1f", health);
    }

    private static int withAlpha(int rgb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | rgb & 0x00FFFFFF;
    }

    private void renderSwitchHint(GuiGraphics graphics, Component hint) {
        int textWidth = font.width(hint);
        int x = (width - textWidth) / 2;
        int y = height - 18;
        float alpha = RadialWheelAnimations.overallAlpha(this);
        int textAlpha = Math.round(0xE8 * alpha);
        if (textAlpha < 4) return;
        graphics.drawString(font, hint, x, y,
                textAlpha << 24 | 0x00FFFFFF, true);
    }

    @Override
    public void renderSectionBackground(
            GuiGraphics graphics,
            int section,
            double x,
            double y,
            float partialTick,
            int mouseX,
            int mouseY,
            float red,
            float green,
            float blue) {
        boolean selected = RadialWheelAnimations.isSectionSelected(this, section, mouseX, mouseY);
        String style = menu.isOrganic() ? "organic" : "goo";
        // Changed provides eight radial backgrounds; later scrolling entries reuse them.
        ResourceLocation texture = Changed.modResource(
                "textures/gui/radial/" + style
                        + (selected ? "_selected/" : "/") + (section % 8) + ".png");
        boolean used = isUsedNegotiationSection(section + RadialWheelAnimations.viewOffset(this));
        graphics.setColor(red, green, blue,
                RadialWheelAnimations.layerAlpha(this)
                        * (used ? 0.42F : 1.0F));
        RadialWheelAnimations.drawFrame(this, graphics, texture, (int)x - 32 + leftPos, (int)y - 32 + topPos,
                0.0F, 0.0F, 64, 64, 64, 64);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void renderSectionForeground(
            GuiGraphics graphics,
            int section,
            double x,
            double y,
            float partialTick,
            int mouseX,
            int mouseY,
            float red,
            float green,
            float blue,
            float alpha) {
        if (section < 0 || section >= actions.size()) {
            return;
        }
        Action action = actions.get(section);
        ResourceLocation icon = action.texture();
        if (action.command().equals("social_seat") || action.command().equals("social_cancel_seat") || action.command().equals("social_disembark"))
            icon = CompanionSeatIcons.resolve(icon);
        int centerX = (int)x + leftPos;
        int centerY = (int)y + topPos;
        float iconAlpha = isUsedNegotiationSection(section)
                ? alpha * 0.3F : alpha;
        graphics.setColor(0.0F, 0.0F, 0.0F, 0.45F * iconAlpha);
        blitScaledIcon(graphics, icon, centerX + 2, centerY + 2,
                34, action.textureSize());
        graphics.setColor(red, green, blue, iconAlpha);
        blitScaledIcon(graphics, icon, centerX, centerY,
                32, action.textureSize());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draws only the icon's native pixels, then scales the pose. Sampling a
     * 38x38 region from a 16x16 or 32x32 texture makes Minecraft wrap the UVs,
     * which is what produced the repeated, garbled-looking wheel icons.
     */
    private static void blitScaledIcon(
            GuiGraphics graphics,
            ResourceLocation icon,
            int centerX,
            int centerY,
            int targetSize,
            int sourceSize) {
        float scale = (float)targetSize / (float)sourceSize;
        graphics.pose().pushPose();
        graphics.pose().translate(
                centerX - targetSize * 0.5F,
                centerY - targetSize * 0.5F,
                0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(icon, 0, 0, 0.0F, 0.0F,
                sourceSize, sourceSize, sourceSize, sourceSize);
        graphics.pose().popPose();
    }

    @Override
    public boolean handleClicked(int section, SingleRunnable close) {
        if (section < 0 || section >= actions.size()) {
            return false;
        }
        if (isUsedNegotiationSection(section)) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(
                            SoundEvents.UI_BUTTON_CLICK, 0.65F));
            return false;
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        String command = actions.get(section).command();
        if ("social_voluntary_transfur".equals(command) || "social_friendly_transfur".equals(command)) {
            voluntaryConfirmationArmed = true;
        }
        if ("social_follow".equals(command)) {
            if (!menu.canFriendFollow()) {
                CompoundTag payload = new CompoundTag();
                payload.putString("command", command);
                menu.setDirty(payload);
                return true;
            }
            menu.toggleLocalFollowing();
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("command", command);
        if ("social_negotiation".equals(command)) {
            RadialWheelAnimations.requestNegotiationSwitch(
                    this, () -> menu.setDirty(payload));
            return false;
        }
        menu.setDirty(payload);
        // Each argument is one conversational turn. Close normally after the
        // choice; the player can reopen the negotiation for the next turn.
        return true;
    }

    private boolean isUsedNegotiationSection(int section) {
        if (!menu.isNegotiationMode()
                || section < 0 || section >= actions.size()) {
            return false;
        }
        return Approach.fromCommand(actions.get(section).command())
                .map(menu::isNegotiationApproachUsed)
                .orElse(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean manualClose = keyCode == 256
                || minecraft.options.keyInventory.matches(keyCode, scanCode);
        if (manualClose
                && !menu.isBondedMode()
                && !menu.isNegotiationMode()
                && menu.hasTrustedRelationship()
                && !manualGoodbyeSent) {
            manualGoodbyeSent = true;
            CompoundTag payload = new CompoundTag();
            payload.putString("command", "social_goodbye");
            menu.setDirty(payload);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private record Action(
            String command,
            ResourceLocation texture,
            int textureSize) {
    }

}
