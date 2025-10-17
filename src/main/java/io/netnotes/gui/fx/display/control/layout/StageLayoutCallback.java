package io.netnotes.gui.fx.display.control.layout;

/**
 * Callback interface for stage positioning
 */
@FunctionalInterface
interface StageLayoutCallback {
    StageLayout calculate(StageContext context);
}