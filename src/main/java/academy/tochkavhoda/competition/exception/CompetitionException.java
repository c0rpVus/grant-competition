package academy.tochkavhoda.competition.exception;

public class CompetitionException extends RuntimeException {
  private final String code;

  public CompetitionException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
