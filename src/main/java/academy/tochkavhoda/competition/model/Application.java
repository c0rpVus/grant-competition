package academy.tochkavhoda.competition.model;

import java.math.BigDecimal;
import java.util.Set;

public record Application(
    String id,
    String ownerId,
    String title,
    String description,
    Set<String> directions,
    BigDecimal amount) {}
