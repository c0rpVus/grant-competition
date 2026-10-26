package academy.tochkavhoda.competition.dto.request;

public record RegisterExpertRequest(
    String firstName,
    String lastName,
    java.util.Set<String> directions,
    String login,
    String password) {}
