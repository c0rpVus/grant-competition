package academy.tochkavhoda.competition.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CompetitionState(BigDecimal fund, BigDecimal threshold, Instant deadline) {}
