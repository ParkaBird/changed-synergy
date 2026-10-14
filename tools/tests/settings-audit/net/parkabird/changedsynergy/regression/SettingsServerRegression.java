package net.parkabird.changedsynergy.regression;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.synergyfixture.SettingsWorkFishingGoal;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.ServerConfigSnapshotPacket;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class SettingsServerRegression {
    private static int checks;
    private static final CommandSource SILENT = new CommandSource() {
        public void sendSystemMessage(net.minecraft.network.chat.Component message) {}
        public boolean acceptsSuccess() { return true; }
        public boolean acceptsFailure() { return true; }
        public boolean shouldInformAdmins() { return false; }
    };

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        try {
            MinecraftServer server = event.getServer();
            Path output = Path.of(System.getProperty("settings.audit.output"));
            Files.createDirectories(output);
            Path file = output.resolve("persisted-common.toml");
            try (var config = CommentedFileConfig.builder(file).sync().build()) {
                config.load();
                ChangedSynergyConfig.SPEC.setConfig(config);
                ChangedSynergyConfig.SPEC.correct(config);
                var source = server.createCommandSourceStack().withSource(SILENT).withPermission(4);
                var publicOptions = ChangedSynergyConfig.options();
                check(publicOptions.size() == 116, "116 public common settings: " + publicOptions.size());
                check(!publicOptions.containsValue(ChangedSynergyConfig.COMMON.behaviourConfigRevision), "Migration marker stays internal");
                for (var entry : publicOptions.entrySet()) {
                    String key = entry.getKey();
                    var option = entry.getValue();
                    ForgeConfigSpec.ValueSpec spec = ChangedSynergyConfig.SPEC.getSpec().get(option.getPath());
                    check(spec.test(option.getDefault()), "Valid default: " + key);
                    check(run(server, source, "config get " + key) == 1, "Get: " + key);
                    check(run(server, source, "config reset " + key.toLowerCase(Locale.ROOT)) == 1, "Reset case insensitive: " + key);
                    String encoded = new com.google.gson.Gson().toJson(option.getDefault());
                    if (option.getDefault() instanceof Enum<?> e) encoded = e.name().toLowerCase(Locale.ROOT);
                    check(run(server, source, "config set " + key + " " + encoded) == 1, "Set round trip: " + key);
                    check(option.get().equals(option.getDefault()), "Preserved type/default: " + key);
                    if (spec.getRange() != null) {
                        for (Object bound : List.of(spec.getRange().getMin(), spec.getRange().getMax()))
                            check(run(server, source, "config set " + key + " " + bound) == 1, "Numeric endpoint: " + key);
                        check(run(server, source, "config set " + key + " NaN") == 0, "Reject nonfinite: " + key);
                        check(run(server, source, "config set " + key + " 999999999999999999999999999") == 0, "Reject overflow: " + key);
                    }
                    check(run(server, source, "config set " + key + " invalid_value") == 0, "Reject invalid type: " + key);
                    run(server, source, "config reset " + key);
                }
                check(run(server, source, "config categories") > 0, "Category command");
                for (String section : publicOptions.keySet().stream().map(k -> k.split("\\.")[0]).distinct().toList())
                    check(run(server, source, "config list " + section.toLowerCase(Locale.ROOT) + " 1") > 0, "Category listing: " + section);
                check(run(server, source, "config list 2") == 116, "Original numeric pagination");
                check(run(server, source, "config list 9999") == 0, "Reject invalid page");
                check(run(server, source, "config get BEHAVIOUR.BehaviourConfigRevision") == 0, "Internal marker cannot be queried");
                try {
                    run(server, source.withPermission(0), "config set TAKEOVER.Enabled false");
                    throw new AssertionError("Non-operator changed server config");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { checks++; }
                var dispatcher = server.getCommands().getDispatcher();
                var completions = dispatcher.getCompletionSuggestions(dispatcher.parse("changedsynergy config set TAKEOVER.Enabled ", source)).get();
                check(completions.getList().stream().map(s -> s.getText()).toList().containsAll(List.of("true", "false")), "Boolean completion");
                check(run(server, source, "config set RELATIONSHIPS.BondTokens []") == 1, "Clear token overrides");
                for (String invalid : List.of("[\"dark=minecraft:quartz,0\"]", "[\"dark=minecraft:quartz,65\"]", "[\"dark=minecraft:air,1\"]",
                        "[\"unknown=minecraft:quartz,1\"]", "[\"dark=minecraft:quartz,1\",\"dark=minecraft:diamond,1\"]", "[1]", "[{\"a\":1}]"))
                    check(run(server, source, "config set RELATIONSHIPS.BondTokens " + invalid) == 0, "Reject invalid token table: " + invalid);
                check(run(server, source, "config set RELATIONSHIPS.BondTokens dark=optionalmod:token,4;white=minecraft:quartz") == 1, "Partial and optional-mod token table");
                run(server, source, "config reset RELATIONSHIPS.BondTokens");
                check(run(server, source, "config set BEHAVIOUR.DisabledOriginForms []") == 1, "Clear form blacklist");
                check(run(server, source, "config set BEHAVIOUR.DisabledOriginForms [\"Bad ID\"]") == 0, "Reject malformed form ID");
                check(run(server, source, "config set COMMUNITIES.PlayerOutpostHaulingBlockedItems minecraft:diamond,example:optional_item") == 1,
                        "Set the pickup blocklist through server commands");
                check(ChangedSynergyConfig.COMMON.playerOutpostHaulingBlockedItems.get().equals(List.of("minecraft:diamond", "example:optional_item")),
                        "Pickup filter preserves both namespaced item IDs");
                check(run(server, source, "config set COMMUNITIES.PlayerOutpostHaulingBlockedItems [\"bad id\"]") == 0,
                        "Server rejects a malformed pickup ID");
                check(run(server, source, "config set COMMUNITIES.PlayerOutpostHaulingBlockedItems []") == 1,
                        "Server can clear the pickup blocklist");
                check(run(server, source, "config set COMMUNITIES.PlayerOutpostCrewBlockedOres minecraft:diamond_ore,example:optional_ore") == 1,
                        "Server config edits travelling ore blocklist");
                check(ChangedSynergyConfig.COMMON.playerOutpostCrewBlockedOres.get().equals(List.of("minecraft:diamond_ore", "example:optional_ore")),
                        "Ore blocklist preserves namespaced optional-mod block IDs");
                check(run(server, source, "config set COMMUNITIES.PlayerOutpostCrewBlockedOres [\"bad id\"]") == 0, "Malformed ore block ID rejected");
                check(run(server, source, "config set COMMUNITIES.PlayerOutpostCrewBlockedOres []") == 1, "Server clears ore blocklist");
                run(server, source, "config set BEHAVIOUR.SearchSpeed 0.12345");
                config.load();
                ChangedSynergyConfig.SPEC.setConfig(config);
                check(ChangedSynergyConfig.COMMON.huntSearchSpeed.get() == 0.12345, "Command value persists with full precision");
                run(server, source, "config reset BEHAVIOUR.SearchSpeed");
                worldRules(server, source);
                runtimeGuards(server);
                var catalog = new LinkedHashMap<String, Object>();
                catalog.put("common", metadata(publicOptions, ChangedSynergyConfig.SPEC));
                var clients = new TreeMap<String, ForgeConfigSpec.ConfigValue<?>>();
                for (var field : ChangedSynergyClientConfig.Client.class.getFields()) {
                    var value = (ForgeConfigSpec.ConfigValue<?>)field.get(ChangedSynergyClientConfig.CLIENT);
                    clients.put(String.join(".", value.getPath()), value);
                }
                check(clients.size() == 16, "16 client preferences");
                catalog.put("client", metadata(clients, ChangedSynergyClientConfig.SPEC));
                Files.writeString(output.resolve("options-runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(catalog));
            }
            System.out.println("SETTINGS_SERVER_REGRESSION_PASS checks=" + checks);
            event.getServer().halt(false);
        } catch (Throwable failure) {
            System.err.println("SETTINGS_SERVER_REGRESSION_FAIL"); failure.printStackTrace();
            event.getServer().halt(false);
        }
    }

    private static int run(MinecraftServer server, CommandSourceStack source, String tail) throws Exception {
        return server.getCommands().getDispatcher().execute("changedsynergy " + tail, source);
    }
    private static Map<String, Object> metadata(Map<String, ForgeConfigSpec.ConfigValue<?>> options, ForgeConfigSpec spec) {
        var result = new LinkedHashMap<String, Object>();
        options.forEach((key, option) -> {
            ForgeConfigSpec.ValueSpec definition = spec.getSpec().get(option.getPath());
            var entry = new LinkedHashMap<String, Object>();
            entry.put("default", option.getDefault());
            entry.put("range", definition.getRange() == null ? null : definition.getRange().toString());
            entry.put("comment", definition.getComment());
            if (option.getDefault() instanceof Enum<?> mode) entry.put("choices", Arrays.stream(mode.getDeclaringClass().getEnumConstants()).map(Enum::name).toList());
            result.put(key, entry);
        });
        return result;
    }
    private static void worldRules(MinecraftServer server, CommandSourceStack source) throws Exception {
        check(ChangedSynergyGameRules.settings().size() == 14, "14 gamerules");
        check(ChangedSynergyGameRules.settings().stream().map(s -> s.name()).distinct().count() == 14, "Unique gamerule catalog");
        for (var setting : ChangedSynergyGameRules.settings()) {
            String next = setting.defaultValue() instanceof Boolean ? "false" : "-37";
            server.getCommands().getDispatcher().execute("gamerule " + setting.name() + " " + next, source);
            var snapshot = ServerConfigSnapshotPacket.capture(server);
            check(snapshot.getString("gamerule." + setting.name()).equals(next), "Actual world value synchronized: " + setting.name());
            server.getCommands().getDispatcher().execute("gamerule " + setting.name() + " " + setting.defaultValue(), source);
        }
        server.getGameRules().getRule(ChangedSynergyGameRules.INITIAL_FACTION_REPUTATION).set(-999, server);
        check(ChangedSynergyGameRules.initialFactionReputation(server.overworld()) == -100, "Legacy world reputation clamps safely");
        server.getGameRules().getRule(ChangedSynergyGameRules.INITIAL_FACTION_REPUTATION).set(0, server);
        var snapshot = ServerConfigSnapshotPacket.capture(server);
        check(snapshot.getAllKeys().size() == ChangedSynergyConfig.options().size() + ChangedSynergyGameRules.settings().size(),
                "Snapshot has public common settings and all gamerules");
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            ServerConfigSnapshotPacket.encode(new ServerConfigSnapshotPacket(snapshot), buffer);
            check(ServerConfigSnapshotPacket.decode(buffer).values().equals(snapshot), "Existing packet wire round trip");
        } finally { buffer.release(); }
    }

    private static void checkPlayerWildSpacing(net.minecraft.server.level.ServerLevel level,
            ChangedEntity mob, BlockPos marker) throws Exception {
        Class<?> searchType = Class.forName(CreatureSettlementService.class.getName() + "$PlacementSearch");
        var constructor = searchType.getDeclaredConstructor(ChangedEntity.class);
        constructor.setAccessible(true);
        var occupied = CreatureSettlementService.class.getDeclaredMethod("isSettlementAreaOccupied",
                net.minecraft.server.level.ServerLevel.class, BlockPos.class, searchType);
        occupied.setAccessible(true);
        int original = ChangedSynergyConfig.COMMON.playerWildOutpostMinimumSpacing.get();
        int loaded = level.getChunkSource().getLoadedChunksCount();
        try {
            check((boolean)occupied.invoke(null, level, marker.offset(32, 100, 0), constructor.newInstance(mob)),
                    "Default player/wild separation protects the horizontal camp footprint across floors");
            BlockPos distant = marker.offset(96, 0, 0);
            check(!(boolean)occupied.invoke(null, level, distant, constructor.newInstance(mob)),
                    "Default 48-block camp buffer leaves distant wild placement available");
            ChangedSynergyConfig.COMMON.playerWildOutpostMinimumSpacing.set(128);
            check((boolean)occupied.invoke(null, level, distant, constructor.newInstance(mob)),
                    "Increasing player/wild spacing rejects a formerly available wild site");
            ChangedSynergyConfig.COMMON.playerWildOutpostMinimumSpacing.set(16);
            check(!(boolean)occupied.invoke(null, level, marker.offset(32, 0, 0), constructor.newInstance(mob)),
                    "Reducing player/wild spacing opens a site without changing wild/wild spacing");
            check((boolean)occupied.invoke(null, level, marker.offset(8, 0, 0), constructor.newInstance(mob)),
                    "The configured smaller camp buffer still rejects nearby wild claims");
            check(level.getChunkSource().getLoadedChunksCount() == loaded,
                    "Configurable camp spacing never loads candidate chunks");
            check(PlayerOutpostData.get(level.getServer()).byOwner(
                    PlayerOutpostData.get(level.getServer()).all().stream()
                            .filter(c -> c.marker.equals(marker)).findFirst().orElseThrow().owner).isPresent(),
                    "Changing separation leaves existing camp records intact");
        } finally { ChangedSynergyConfig.COMMON.playerWildOutpostMinimumSpacing.set(original); }
    }

    private static void runtimeGuards(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var owner = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "SettingsAudit"));
        var mob = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse("changed:dark_latex_wolf_male")).create(level);
        var data = PlayerOutpostData.get(server);
        var marker = new BlockPos(80, 90, 80);
        level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState(), 3);
        for (var old : List.copyOf(data.all())) if (old.marker.equals(marker)) data.disband(old.owner);
        check(data.claim(level, owner.getUUID(), marker).equals("claimed"), "Create camp fixture");
        var camp = data.byOwner(owner.getUUID()).orElseThrow();
        checkPlayerWildSpacing(level, mob, marker);
        data.add(camp, mob.getUUID(), "Worker", camp.dimension, marker, PlayerOutpostSupplyType.ORANGE);
        PlayerOutpostService.applyRole(mob, owner, PlayerOutpostData.Role.SUPPLY);
        ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(true);
        check(PlayerOutpostService.operational(mob), "Friend camp enabled");
        level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(false, server);
        mob.tickCount = 100; PlayerOutpostService.onTick(new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mob));
        check(!PlayerOutpostService.operational(mob) && camp.members.containsKey(mob.getUUID()), "Friendship off pauses instead of removing member");
        level.getGameRules().getRule(ChangedSynergyGameRules.FRIENDSHIP_SYSTEM).set(true, server);
        check(PlayerOutpostService.operational(mob), "Friendship on resumes assignment");
        level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(false, server);
        check(!PlayerOutpostWorkGoal.canWork(mob, camp) && !PlayerOutpostService.enabled(level), "Creature Life stops camp work");
        check(PlayerOutpostData.isAreaClaimed(level, marker, 1), "Disabled camp footprint remains protected");
        level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(true, server);
        level.getGameRules().getRule(ChangedSynergyGameRules.NPC_AI).set(false, server);
        check(CreatureLifeMemory.enabled(mob) && PlayerOutpostService.operational(mob), "Hostility AI off does not disable unrelated creature life");
        level.getGameRules().getRule(ChangedSynergyGameRules.NPC_AI).set(true, server);
        data.remove(camp, mob.getUUID()); PlayerOutpostService.clearAssignment(mob);
        mob.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).toList().forEach(mob.goalSelector::removeGoal);
        var original = new SettingsWorkFishingGoal();
        mob.goalSelector.addGoal(4, original);
        PlayerOutpostService.ensureCompanionWorkControls(mob);
        var wrapped = mob.goalSelector.getAvailableGoals().iterator().next();
        check(wrapped.getPriority() == 4 && wrapped.getFlags().equals(original.getFlags()), "Native work priority and mutex flags preserved");
        var guarded = wrapped.getGoal();
        check(guarded != original && PlayerOutpostService.underlyingCompanionTask(guarded) == original, "Same native work instance retained");
        for (int i = 0; i < 12; i++) {
            PlayerOutpostService.ensureCompanionWorkControls(mob);
            check(mob.goalSelector.getAvailableGoals().size() == 1, "No duplicate or nested work guards");
        }
        mob.goalSelector.tick();
        check(original.starts == 1 && original.ticks > 0, "Native work executes normally");
        check(BondedPetSettings.hasRunningUtilityGoal(mob), "Following still recognizes guarded native work");
        var runningWork = CompanionWorkDialogue.class.getDeclaredMethod("runningNativeWork", ChangedEntity.class);
        runningWork.setAccessible(true);
        check(runningWork.invoke(null, mob).toString().equals("FISHING"), "Work dialogue still recognizes guarded fishing");
        ChangedSynergyConfig.COMMON.companionWorkAi.set(false); mob.goalSelector.tick();
        check(!guarded.canUse() && !guarded.canContinueToUse() && original.stops == 1, "Config off stops running native work");
        check(!BondedPetSettings.hasRunningUtilityGoal(mob) && runningWork.invoke(null, mob) == null,
                "Stopped work releases following and cannot emit work reactions");
        ChangedSynergyConfig.COMMON.companionWorkAi.set(true);
        check(guarded.canUse(), "Config on restores native work");
        level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(false, server);
        check(!guarded.canUse() && !guarded.canContinueToUse(), "Bond rule stops native work");
        level.getGameRules().getRule(ChangedSynergyGameRules.BOND_SYSTEM).set(true, server);
        level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(false, server);
        check(!guarded.canUse(), "Creature Life rule stops native work");
        level.getGameRules().getRule(ChangedSynergyGameRules.CREATURE_LIFE).set(true, server);
        ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(false);
        data.disband(owner.getUUID()); mob.discard();
        System.out.println("SETTINGS_RUNTIME_GUARDS_PASS");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
