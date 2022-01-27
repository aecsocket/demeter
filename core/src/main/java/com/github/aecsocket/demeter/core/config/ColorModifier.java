package com.github.aecsocket.demeter.core.config;

import java.lang.reflect.Type;

import com.github.aecsocket.minecommons.core.serializers.Serializers;
import com.github.aecsocket.minecommons.core.vector.cartesian.Vector3;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

public final class ColorModifier {
    private final Vector3 value;
    private final boolean hsv;
    private final double alpha;

    private ColorModifier(Vector3 value, boolean hsv, double alpha) {
        this.value = value;
        this.hsv = hsv;
        this.alpha = alpha;
    }

    public static ColorModifier hsv(Vector3 value) { return new ColorModifier(value, true, 0); }
    public static ColorModifier rgba(Vector3 value, double alpha) { return new ColorModifier(value, false, alpha); }

    public Vector3 value() { return value; }
    public boolean hsv() { return hsv; }
    public double alpha() { return alpha; }

    public Vector3 combineOn(Vector3 baseRgb) {
        if (hsv)
            return baseRgb.rgbToHsv().add(value).hsvToRgb();
        else
            return baseRgb.lerp(value, alpha);
    }

    public static final class Serializer implements TypeSerializer<ColorModifier> {
        public static final String HSV = "hsv";
        public static final String RGBA = "rgba";

        @Override
        public void serialize(Type type, @Nullable ColorModifier obj, ConfigurationNode node) throws SerializationException {
            if (obj == null) node.set(null);
            else {
                if (obj.hsv) {
                    node.appendListNode().set(HSV);
                    node.appendListNode().set(obj.value);
                } else {
                    node.appendListNode().set(RGBA);
                    node.appendListNode().set(obj.value);
                    node.appendListNode().set(obj.alpha);
                }
            }
        }

        @Override
        public ColorModifier deserialize(Type type, ConfigurationNode node) throws SerializationException {
            var nodes = node.childrenList();
            if (nodes.size() < 1)
                throw new SerializationException(node, type, "Must be a list starting with first element of either `" + HSV + "` or `" + RGBA + "`");
            String op = Serializers.require(nodes.get(0), String.class);
            switch (op) {
                case HSV -> {
                    if (nodes.size() < 2)
                        throw new SerializationException(node, type, "Must be list of [ 'hsv', HSV color modifier ]");
                    return ColorModifier.hsv(Serializers.require(nodes.get(1), Vector3.class));
                }
                case RGBA -> {
                    if (nodes.size() < 3)
                        throw new SerializationException(node, type, "Must be list of [ 'rgba', RGB value, alpha value ]");
                    return ColorModifier.rgba(
                            Serializers.require(nodes.get(1), Vector3.class),
                            nodes.get(2).getDouble()
                    );
                }
            }
            throw new SerializationException(node, type, "Unknown color modifier type `" + op + "`");
        }
    }
}
