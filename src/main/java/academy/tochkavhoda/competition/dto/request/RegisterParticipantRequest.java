package academy.tochkavhoda.competition.dto.request;

public record RegisterParticipantRequest(
    String firstName, String lastName, String company, String login, String password) {}
