package io.netnotes.gui.fx.app.control.layout;

/**
 * Callback interface for node layout calculation
 */
@FunctionalInterface
public interface LayoutCallback {
    LayoutData calculate(LayoutContext context);
}
