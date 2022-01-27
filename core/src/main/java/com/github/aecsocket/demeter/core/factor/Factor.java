package com.github.aecsocket.demeter.core.factor;

public interface Factor<F extends Factor<F>> {
    F add(F other);
}
