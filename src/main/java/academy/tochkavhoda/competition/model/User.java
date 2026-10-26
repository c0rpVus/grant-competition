package academy.tochkavhoda.competition.model;

import java.util.Set;

public record User(
    String id,
    String firstName,
    String lastName,
    String company,
    Set<String> directions,
    String login,
    String passwordHash,
    String salt,
    String role) {}
