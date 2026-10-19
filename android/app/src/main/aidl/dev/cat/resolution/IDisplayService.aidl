package dev.cat.resolution;

interface IDisplayService {
    void destroy() = 16777114;
    String inspect() = 1;
    String apply(double multiplier, int timeoutSeconds) = 2;
    String confirm() = 3;
    String revert() = 4;
    String restore() = 5;
}
