package com.github.aecsocket.demeter.core.factor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Factors<F extends Factor<F>> implements Iterable<F> {
    public static final class Builder<F extends Factor<F>> {
        private final LinkedHashMap<String, F> factors = new LinkedHashMap<>();

        public Builder<F> add(String key, F factor) {
            factors.put(key, factor);
            return this;
        }

        public Factors<F> build() {
            if (factors.size() == 0)
                throw new IllegalStateException("Must have at least one factor");
            return new Factors<>(factors);
        }
    }

    public static <F extends Factor<F>> Builder<F> builder() {
        return new Builder<>();
    }

    private final LinkedHashMap<String, F> factors;
    private F computed;

    private Factors(LinkedHashMap<String, F> factors) {
        this.factors = factors;
    }

    public Map<String, F> factors() { return new HashMap<>(factors); }

    public F get() {
        if (computed != null)
            return computed;
        for (F next : factors.values()) {
            if (computed == null)
                computed = next;
            else
                computed = computed.add(next);
        }
        return computed;
    }

    public F get(String key) {
        return factors.get(key);
    }

    @Override
    public Iterator<F> iterator() {
        return factors.values().iterator();
    }
}
