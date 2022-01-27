package com.github.aecsocket.demeter.core.config;

import java.util.Locale;

import com.github.aecsocket.minecommons.core.biome.Precipitation;
import com.github.aecsocket.minecommons.core.i18n.I18N;
import com.github.aecsocket.minecommons.core.i18n.Renderable;
import com.github.aecsocket.minecommons.core.vector.cartesian.Vector3;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.meta.NodeKey;
import org.spongepowered.configurate.objectmapping.meta.Required;

import net.kyori.adventure.text.Component;

public record Season(
    @NodeKey String name,
    @Required Vector3 color,
    @Nullable ColorModifier foliageColor,
    @Nullable ColorModifier grassColor,
    @Nullable ColorModifier fogColor,
    @Nullable ColorModifier skyColor,
    @Nullable ColorModifier waterColor,
    @Nullable ColorModifier waterFogColor,
    @Nullable Precipitation precipitation
) implements Renderable {
    public static final String SEASON = "season";

    @Override
    public Component render(I18N i18n, Locale locale) {
        return i18n.line(locale, SEASON + "." + name);
    }
}
