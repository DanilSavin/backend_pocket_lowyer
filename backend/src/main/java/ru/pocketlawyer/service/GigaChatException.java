package ru.pocketlawyer.service;

public class GigaChatException extends RuntimeException {
    public GigaChatException(String message) { super(message); }
    public GigaChatException(String message, Throwable cause) { super(message, cause); }
}
