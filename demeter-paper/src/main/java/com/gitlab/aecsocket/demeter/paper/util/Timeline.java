package com.gitlab.aecsocket.demeter.paper.util;

import com.gitlab.aecsocket.minecommons.core.Numbers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Timeline {
    public record Section(TextColor color, double percent) {}

    private final int length;
    private double complete;
    private final List<Section> sections = new ArrayList<>();

    public Timeline(int length) {
        this.length = length;
    }

    public int length() { return length; }

    public double complete() { return complete; }
    public Timeline complete(double complete) { this.complete = complete; return this; }

    public List<Section> sections() { return sections; }
    public Timeline add(Section... sections) { this.sections.addAll(Arrays.asList(sections)); return this; }

    public Component build() {
        TextComponent[] components = new TextComponent[length];
        Arrays.fill(components, Component.text(" "));
        int completeIdx = Numbers.clamp((int) (complete * length), 0, length - 1);
        components[completeIdx] = components[completeIdx].content("|");

        double current = 0;
        for (var section : sections) {
            double percent = section.percent;
            int start = (int) Math.round(current * length);
            for (int i = start; i < start + (percent * length); i++) {
                int clamped = Numbers.clamp(i, 0, length - 1);
                components[clamped] = components[clamped].color(section.color);
            }
            current += percent;
            if (current >= 1)
                break;
        }

        var builder = Component.text().decorate(TextDecoration.STRIKETHROUGH);
        for (var component : components)
            builder.append(component);
        return builder.build();
    }
}
