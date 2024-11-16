package net.twoturtles;

// This must be in a separate file from MouseMixin. You can't create a new method in a Mixin,
// but adding it via an interface works.
public interface MouseMixinInterface {
    void onCursorPosAgent$Mixin(long window, double x, double y);
}