package com.nkd.accountservice.domain;

public record Response(String status, String message, Object data) {}
