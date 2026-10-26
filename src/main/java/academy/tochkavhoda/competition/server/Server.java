package academy.tochkavhoda.competition.server;

import academy.tochkavhoda.competition.dto.response.ServerResponse;
import academy.tochkavhoda.competition.service.CompetitionService;
import java.math.BigDecimal;
import java.time.*;

/** Public JSON API. All business logic is delegated to the service. */
public final class Server {
  private final CompetitionService service;

  public Server(BigDecimal fund, BigDecimal threshold, Instant deadline) {
    this(fund, threshold, deadline, Clock.systemUTC());
  }

  public Server(BigDecimal fund, BigDecimal threshold, Instant deadline, Clock clock) {
    service = new CompetitionService(fund, threshold, deadline, clock);
  }

  public ServerResponse registerParticipant(String requestJsonString) {
    return service.registerParticipant(requestJsonString);
  }

  public ServerResponse registerExpert(String requestJsonString) {
    return service.registerExpert(requestJsonString);
  }

  public ServerResponse login(String requestJsonString) {
    return service.login(requestJsonString);
  }

  public ServerResponse logout(String token) {
    return service.logout(token);
  }

  public ServerResponse deleteAccount(String token) {
    return service.deleteAccount(token);
  }

  public ServerResponse addApplications(String token, String requestJsonString) {
    return service.addApplications(token, requestJsonString);
  }

  public ServerResponse cancelApplication(String token, String requestJsonString) {
    return service.cancelApplication(token, requestJsonString);
  }

  public ServerResponse rateApplication(String token, String requestJsonString) {
    return service.rateApplication(token, requestJsonString);
  }

  public ServerResponse deleteRating(String token, String requestJsonString) {
    return service.deleteRating(token, requestJsonString);
  }

  public ServerResponse getOwnApplications(String token) {
    return service.getOwnApplications(token);
  }

  public ServerResponse searchApplications(String token, String requestJsonString) {
    return service.searchApplications(token, requestJsonString);
  }

  public ServerResponse getRatedApplications(String token) {
    return service.getRatedApplications(token);
  }

  public ServerResponse getResults(String token) {
    return service.getResults(token);
  }
}
