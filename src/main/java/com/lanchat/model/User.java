package com.lanchat.model;

public record User(String name, String host, int port) {
    @Override public String toString() { return name; }
}