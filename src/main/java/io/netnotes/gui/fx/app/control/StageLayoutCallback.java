package io.netnotes.gui.fx.app.control;

/**
 * Callback interface for stage positioning
 */
@FunctionalInterface
interface StageLayoutCallback {
    StageLayout calculate(StageContext context);
}