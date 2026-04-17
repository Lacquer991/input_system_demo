package org.demo.input.binding;

import org.demo.input.binding.spi.BindingImplProvider;

import java.util.ServiceLoader;

public final class BindingServiceLocator {

    private static volatile BindingImplProvider bindingImplProvider;

    private BindingServiceLocator() {}

    public static BindingImplProvider getBindingImplProvider() {
        BindingImplProvider provider = bindingImplProvider;
        if (provider != null) {
            return provider;
        }

        return bindingImplProvider = ServiceLoader
                .load(BindingImplProvider.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No BindingImplProvider found"));
    }
}
