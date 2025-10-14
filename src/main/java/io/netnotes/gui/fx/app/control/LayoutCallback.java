package io.netnotes.gui.fx.app.control;

/**
 * Callback interface for node layout calculation
 */
@FunctionalInterface
interface LayoutCallback {
    LayoutData calculate(LayoutContext context);
}
