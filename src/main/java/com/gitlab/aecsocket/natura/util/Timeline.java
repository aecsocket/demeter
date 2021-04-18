package com.gitlab.aecsocket.natura.util;

import com.gitlab.aecsocket.unifiedframework.core.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Timeline {
    public static class Section {
        public final TextColor color;
        public final double percent;

        public Section(TextColor color, double percent) {
            this.color = color;
            this.percent = percent;
        }
    }

    private final int length;
    private double complete;
    private final List<Section> sections = new ArrayList<>();

    public Timeline(int length) {
        this.length = length;
    }

    public double complete() { return complete; }
    public Timeline complete(double complete) { this.complete = complete; return this; }

    public List<Section> sections() { return sections; }
    public Timeline addSections(Section... sections) { this.sections.addAll(Arrays.asList(sections)); return this; }

    public Component build() {
        TextComponent[] components = new TextComponent[length];
        Arrays.fill(components, Component.text(" "));
        int completeIdx = Utils.clamp((int) (complete * components.length), 0, length - 1);
        components[completeIdx] = components[completeIdx].content("|");

        double current = 0;
        for (Section section : sections) {
            double percent = section.percent;
            int start = (int) (current * length);
            for (int i = start; i < start + (percent * length); i++) {
                int clamped = Utils.clamp(i, 0, length - 1);
                components[clamped] = components[clamped].color(section.color);
            }
            current += percent;
            if (current >= 1)
                break;
        }

        TextComponent.Builder builder = Component.text().decorate(TextDecoration.STRIKETHROUGH);
        for (TextComponent component : components) {
            builder.append(component);
        }
        return builder.build();
    }
}
