package com.lanchat.model;

import java.time.LocalDateTime;

public record Message(long id, String sender, String recipient,
                      String plaintext, LocalDateTime sentAt) {}