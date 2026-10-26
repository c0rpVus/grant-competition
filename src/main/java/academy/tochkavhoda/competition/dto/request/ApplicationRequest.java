package academy.tochkavhoda.competition.dto.request;

public record ApplicationRequest(
    String title,
    String description,
    java.util.Set<String> directions,
    java.math.BigDecimal amount) {}
