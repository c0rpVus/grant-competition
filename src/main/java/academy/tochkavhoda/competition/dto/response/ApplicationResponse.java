package academy.tochkavhoda.competition.dto.response;

public record ApplicationResponse(
    String id,
    String ownerId,
    String title,
    String description,
    java.util.Set<String> directions,
    java.math.BigDecimal amount,
    java.math.BigDecimal average,
    Integer myScore) {}
