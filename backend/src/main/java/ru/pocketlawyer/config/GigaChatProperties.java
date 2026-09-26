package ru.pocketlawyer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gigachat")
public record GigaChatProperties(String authKey, String scope, String model) { }
