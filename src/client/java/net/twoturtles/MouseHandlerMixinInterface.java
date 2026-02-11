package net.twoturtles;

// This must be in a separate file from MouseHandlerMixin. You can't create a new method in a Mixin,
// but adding it via an interface works.
public interface MouseHandlerMixinInterface {
  void onMoveAgent$Mixin(long window, double x, double y);
}
