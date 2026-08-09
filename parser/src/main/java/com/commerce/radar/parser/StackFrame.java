package com.commerce.radar.parser;

public record StackFrame(String className, String method, String file, String lineNumber, String raw) {
    public String location() {
        return className + "." + method;
    }
}
