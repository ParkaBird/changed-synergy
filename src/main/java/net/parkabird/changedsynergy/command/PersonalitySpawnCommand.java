package net.parkabird.changedsynergy.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Arrays;
import java.util.Locale;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Operator shortcut for spawning a Changed creature with a chosen personality. */
final class PersonalitySpawnCommand {
    private PersonalitySpawnCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("spawn")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("type", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                ForgeRegistries.ENTITY_TYPES.getKeys().stream()
                                        .filter(id -> id.getNamespace().startsWith("changed"))
                                        .map(ResourceLocation::toString), builder))
                        .then(Commands.argument("personality", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(CreaturePersonality.Trait.values())
                                                .map(trait -> trait.name().toLowerCase(Locale.ROOT)),
                                        builder))
                                .executes(context -> spawn(context.getSource(),
                                        ResourceLocationArgument.getId(context, "type"),
                                        StringArgumentType.getString(context, "personality")))));
    }

    private static int spawn(CommandSourceStack source, ResourceLocation id, String personalityName) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            source.sendFailure(Component.translatable("command.changed_synergy.spawn.invalid_type", id));
            return 0;
        }
        CreaturePersonality.Trait trait;
        try {
            trait = CreaturePersonality.Trait.valueOf(personalityName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("command.changed_synergy.spawn.invalid_personality", personalityName));
            return 0;
        }
        if (!ChangedSynergyGameRules.enabled(source.getLevel(),
                ChangedSynergyGameRules.PERSONALITY_SYSTEM)) {
            source.sendFailure(Component.translatable("command.changed_synergy.spawn.personality_disabled"));
            return 0;
        }
        Entity created = type.create(source.getLevel());
        if (!(created instanceof ChangedEntity creature)
                || !CreatureSocialProfile.allowsSynergySystems(creature)) {
            if (created != null) created.discard();
            source.sendFailure(Component.translatable("command.changed_synergy.spawn.invalid_type", id));
            return 0;
        }
        Vec3 facing = source.getEntity() == null
                ? new Vec3(0, 0, 2)
                : source.getEntity().getLookAngle().multiply(2, 0, 2);
        Vec3 position = source.getPosition().add(facing);
        creature.moveTo(position.x, position.y, position.z, source.getRotation().y, 0);
        creature.finalizeSpawn(source.getLevel(),
                source.getLevel().getCurrentDifficultyAt(BlockPos.containing(position)),
                MobSpawnType.COMMAND, null, null);
        CreaturePersonality.setDominantTrait(creature, trait);
        if (!source.getLevel().addFreshEntity(creature)) {
            creature.discard();
            source.sendFailure(Component.translatable("command.changed_synergy.spawn.failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.changed_synergy.spawn.success",
                creature.getDisplayName(), trait.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
