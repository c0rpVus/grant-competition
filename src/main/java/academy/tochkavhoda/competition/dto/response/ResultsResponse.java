package academy.tochkavhoda.competition.dto.response;

public record ResultsResponse(
    java.util.List<AwardResponse> awards, java.math.BigDecimal remainingFund) {}
