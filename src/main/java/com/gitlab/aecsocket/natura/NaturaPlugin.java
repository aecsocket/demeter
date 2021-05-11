package com.gitlab.aecsocket.natura;

import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.paper.PaperCommandManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.netty.WirePacket;
import com.gitlab.aecsocket.natura.feature.*;
import com.gitlab.aecsocket.natura.util.GrassColors;
import com.gitlab.aecsocket.natura.util.ImageColors;
import com.gitlab.aecsocket.unifiedframework.core.util.log.LogLevel;
import com.gitlab.aecsocket.unifiedframework.core.util.result.LoggingEntry;
import com.gitlab.aecsocket.unifiedframework.paper.util.plugin.BasePlugin;
import com.gitlab.aecsocket.unifiedframework.paper.util.plugin.PluginHook;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.minecraft.server.v1_16_R3.*;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_16_R3.block.CraftBlock;
import org.bukkit.entity.Player;
import org.spongepowered.configurate.serialize.SerializationException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class NaturaPlugin extends BasePlugin {
    public static final int BSTATS_PLUGIN_ID = 10976;
    public static final String PATH_FOLIAGE = "foliage.png";
    public static final String PATH_GRASS = "grass.png";

    public static final int TICKS_PER_DAY = 20 * 60 * 20;
    public static final double HOUR = NaturaPlugin.TICKS_PER_DAY / 24d;
    public static final double MINUTE = HOUR / 60;
    public static final double SECOND = MINUTE / 60;

    public static final List<String> DEFAULT_RESOURCES = Arrays.asList(
            SETTINGS_FILE,
            LANGUAGE_ROOT + "/default_en-US.conf",
            LANGUAGE_ROOT + "/en-US.conf",
            PATH_FOLIAGE,
            PATH_GRASS
    );

    private final Display display = new Display(this);
    private final TimeDilation timeDilation = new TimeDilation(this);
    private final Seasons seasons = new Seasons(this);
    private final Climate climate = new Climate(this);
    private final BodyTemperature bodyTemperature = new BodyTemperature(this);
    private final Weather weather = new Weather(this);
    private final List<Feature> features = Arrays.asList(display, timeDilation, seasons, climate, bodyTemperature, weather);

    private final Map<Biome, BiomeBase> biomeInternalMap = new HashMap<>();
    private final Map<Integer, Biome> biomeIdMap = new HashMap<>();
    private final Map<Player, BossBar> bossBars = new HashMap<>();
    private ImageColors foliageColors;
    private GrassColors grassColors;

    private PaperCommandManager<CommandSender> commandManager;
    private ProtocolManager protocol;

    @Override
    public void onEnable() {
        super.onEnable();
        try {
            commandManager = new PaperCommandManager<>(
                    this,
                    CommandExecutionCoordinator.simpleCoordinator(),
                    Function.identity(),
                    Function.identity()
            );
            NaturaCommand command = new NaturaCommand(this);
            command.register(commandManager);
        } catch (Exception e) {
            log(LogLevel.ERROR, e, "Could not initialize command manager - command functionality will be disabled");
        }

        protocol = ProtocolLibrary.getProtocolManager();
        protocol.addPacketListener(new NaturaPacketAdapter(this));

        Bukkit.getPluginManager().registerEvents(new NaturaListener(this), this);

        for (Biome biome : Biome.values()) {
            biomeInternalMap.put(biome, CraftBlock.biomeToBiomeBase(RegistryGeneration.WORLDGEN_BIOME, biome));
        }

        try {
            IRegistry<BiomeBase> biomeRegistry = RegistryGeneration.WORLDGEN_BIOME;
            @SuppressWarnings("unchecked")
            var biomeIds = (Int2ObjectMap<ResourceKey<BiomeBase>>) getField(BiomeRegistry.class, "c").get(null);
            biomeIds.forEach((id, key) -> biomeIdMap.put(id, CraftBlock.biomeBaseToBiome(biomeRegistry, biomeRegistry.a(key))));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log(LogLevel.WARN, e, "Could not set up biome ID mappings");
        }
    }

    @Override
    public void onDisable() {
        clearBossBars();
        features.forEach(Feature::stop);
    }

    private Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Override protected List<String> defaultResources() { return DEFAULT_RESOURCES; }
    @Override protected List<PluginHook> hooks() { return Collections.emptyList(); }

    @Override
    public boolean load(List<LoggingEntry> result) {
        scheduler.cancel();
        features.forEach(Feature::stop);
        if (super.load(result)) {
            if (setting(n -> n.getBoolean(true), "enable_bstats")) {
                Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
                // TODO add some cool charts
            }

            try {
                foliageColors = ImageColors.load(ImageIO.read(file(PATH_FOLIAGE)));
            } catch (IOException e) {
                result.add(LoggingEntry.of(LogLevel.WARN, e, "Could not load foliage colors from `%s`", PATH_FOLIAGE));
            }
            try {
                grassColors = new GrassColors(ImageColors.load(ImageIO.read(file(PATH_GRASS))));
            } catch (IOException e) {
                result.add(LoggingEntry.of(LogLevel.WARN, e, "Could not load grass colors from `%s`", PATH_GRASS));
            }

            for (Feature feature : features) {
                String id = feature.id();
                try {
                    feature.acceptConfig(setting(n -> n, id));
                } catch (SerializationException e) {
                    result.add(LoggingEntry.of(LogLevel.WARN, e, "Could not load config for `%s`", id));
                }
                feature.start();
                result.add(LoggingEntry.of(LogLevel.VERBOSE, "Loaded `%s`", id));
            }
            return true;
        }
        return false;
    }

    public Display display() { return display; }
    public TimeDilation timeDilation() { return timeDilation; }
    public Seasons seasons() { return seasons; }
    public Climate climate() { return climate; }
    public BodyTemperature bodyTemperature() { return bodyTemperature; }
    public Weather weather() { return weather; }
    public List<Feature> features() { return features; }

    public Map<Biome, BiomeBase> internalBiomes() { return biomeInternalMap; }
    public BiomeBase internalBiome(Biome biome) { return biomeInternalMap.get(biome); }
    public Map<Integer, Biome> biomeIds() { return biomeIdMap; }
    public Biome biomeById(int id) { return biomeIdMap.get(id); }

    public Map<Player, BossBar> bossBars() { return bossBars; }
    public BossBar bossBar(Player player) {
        return bossBars.computeIfAbsent(player, p -> {
            BossBar bar = BossBar.bossBar(Component.empty(),
                    setting(n -> n.getFloat(0f), "boss_bar", "progress"),
                    setting(n -> n.get(BossBar.Color.class, BossBar.Color.WHITE), "boss_bar", "color"),
                    setting(n -> n.get(BossBar.Overlay.class, BossBar.Overlay.PROGRESS), "boss_bar", "overlay")
            );
            // TODO
            bar.addFlag(BossBar.Flag.CREATE_WORLD_FOG);
            // END
            if (setting(n -> n.getBoolean(true), "boss_bar", "enabled")) {
                p.showBossBar(bar);
            }
            return bar;
        });
    }
    public void clearBossBar(Player player) {
        BossBar bar = bossBars.remove(player);
        if (bar != null)
            player.hideBossBar(bar);
    }
    public void clearBossBars() {
        for (var entry : bossBars.entrySet()) {
            entry.getKey().hideBossBar(entry.getValue());
        }
        bossBars.clear();
    }

    public ImageColors foliageColors() { return foliageColors; }
    public GrassColors grassColors() { return grassColors; }

    public PaperCommandManager<CommandSender> commandManager() { return commandManager; }
    public ProtocolManager protocol() { return protocol; }

    public void sendPacket(PacketContainer packet, Player target, boolean wire) {
        try {
            if (wire) {
                WirePacket wirePacket = WirePacket.fromPacket(packet);
                protocol.sendWirePacket(target, wirePacket);
            } else
                protocol.sendServerPacket(target, packet);
        } catch (InvocationTargetException | RuntimeException e) {
            log(LogLevel.WARN, e, "Could not send packet to %s (%s)", target.getName(), target.getUniqueId());
        }
    }

    public void sendPacket(PacketContainer packet, Player target) {
        sendPacket(packet, target, false);
    }

    public void sendPacket(Player target, PacketType type, boolean wire, Consumer<PacketContainer> builder) {
        PacketContainer packet = new PacketContainer(type);
        builder.accept(packet);
        sendPacket(packet, target, wire);
    }

    public void sendPacket(Player target, PacketType type, Consumer<PacketContainer> builder) {
        sendPacket(target, type, false, builder);
    }
}
