package academy.tochkavhoda.competition;

import static org.junit.jupiter.api.Assertions.*;

import academy.tochkavhoda.competition.dto.response.ServerResponse;
import academy.tochkavhoda.competition.server.Server;
import com.google.gson.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

class TestServer {
  static final Instant X = Instant.parse("2030-01-01T00:00:00Z");

  static class Time extends Clock {
    Instant now = X.minusSeconds(10);

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  Time time;
  Server s;
  String p, e;

  JsonObject ok(ServerResponse r) {
    assertEquals(200, r.getResponseCode(), r.getResponseData());
    return JsonParser.parseString(r.getResponseData()).getAsJsonObject();
  }

  void bad(ServerResponse r) {
    assertEquals(400, r.getResponseCode(), r.getResponseData());
    JsonObject o = JsonParser.parseString(r.getResponseData()).getAsJsonObject();
    assertEquals(1, o.size());
    assertFalse(o.get("error").getAsString().isBlank());
  }

  String participant(String login, String company) {
    ok(
        s.registerParticipant(
            "{\"firstName\":\"Анна\",\"lastName\":\"Иванова\",\"company\":\""
                + company
                + "\",\"login\":\""
                + login
                + "\",\"password\":\"password123\"}"));
    return login(login);
  }

  String expert(String login, String last) {
    ok(
        s.registerExpert(
            "{\"firstName\":\"Иван\",\"lastName\":\""
                + last
                + "\",\"directions\":[\"математика\"],\"login\":\""
                + login
                + "\",\"password\":\"password123\"}"));
    return login(login);
  }

  String login(String name) {
    return ok(s.login("{\"login\":\"" + name + "\",\"password\":\"password123\"}"))
        .get("token")
        .getAsString();
  }

  String body(int amount, String direction) {
    return "{\"title\":\"Проект\",\"description\":\"Описание\",\"directions\":[\""
        + direction
        + "\"],\"amount\":"
        + amount
        + "}";
  }

  String add(int amount) {
    return ok(s.addApplications(p, "{\"applications\":[" + body(amount, "математика") + "]}"))
        .getAsJsonArray("applicationIds")
        .get(0)
        .getAsString();
  }

  String id(String id) {
    return "{\"applicationId\":\"" + id + "\"}";
  }

  void rate(String token, String id, int score) {
    ok(s.rateApplication(token, "{\"applicationId\":\"" + id + "\",\"score\":" + score + "}"));
  }

  JsonArray mine() {
    return ok(s.getOwnApplications(p)).getAsJsonArray("applications");
  }

  JsonObject finish() {
    time.now = X;
    return ok(s.getResults(e));
  }

  @BeforeEach
  void setup() {
    time = new Time();
    s = new Server(new BigDecimal("100"), new BigDecimal("3"), X, time);
    p = participant("part", "Фирма");
    e = expert("expert", "Петров");
  }

  @Test
  void tokensAndLogout() {
    String p2 = login("part");
    assertNotEquals(p, p2);
    ok(s.logout(p));
    bad(s.getOwnApplications(p));
    ok(s.getOwnApplications(p2));
    bad(s.logout(p));
    bad(s.getOwnApplications(null));
  }

  @Test
  void badLogin() {
    bad(s.login("{\"login\":\"part\",\"password\":\"wrong\"}"));
    bad(s.login("{}"));
  }

  @Test
  void duplicateIdentity() {
    bad(
        s.registerParticipant(
            "{\"firstName\":\"A\",\"lastName\":\"B\",\"company\":\"фирма\",\"login\":\"other\",\"password\":\"password123\"}"));
    bad(
        s.registerExpert(
            "{\"firstName\":\"Иван\",\"lastName\":\"Петров\",\"directions\":[\"физика\"],\"login\":\"other\",\"password\":\"password123\"}"));
    bad(
        s.registerExpert(
            "{\"firstName\":\"A\",\"lastName\":\"B\",\"directions\":[\"физика\"],\"login\":\"PART\",\"password\":\"password123\"}"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "null",
        "[]",
        "{",
        "1",
        "{}",
        "{\"firstName\":2}",
        "{\"firstName\":\"\",\"password\":\"x\"}"
      })
  void malformedRegistration(String json) {
    bad(s.registerParticipant(json));
  }

  @Test
  void multipleApplicationsAndAtomicBatch() {
    ok(
        s.addApplications(
            p, "{\"applications\":[" + body(10, "математика") + "," + body(20, "физика") + "]}"));
    assertEquals(2, mine().size());
    bad(
        s.addApplications(
            p,
            "{\"applications\":[" + body(30, "математика") + "," + body(-1, "математика") + "]}"));
    assertEquals(2, mine().size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "{}",
        "{\"applications\":[]}",
        "{\"applications\":[null]}",
        "{\"applications\":[{\"title\":123}]}",
        "{\"applications\":[{\"title\":\"A\",\"description\":\"B\",\"directions\":[\"x\"],\"amount\":\"10\"}]}"
      })
  void invalidApplications(String json) {
    bad(s.addApplications(p, json));
    assertEquals(0, mine().size());
  }

  @Test
  void ownershipAndRoles() {
    String a = add(10);
    String other = participant("part2", "Другая");
    bad(s.cancelApplication(other, id(a)));
    bad(s.addApplications(e, "{}"));
    bad(s.rateApplication(p, id(a)));
    bad(s.getOwnApplications(e));
    bad(s.getRatedApplications(p));
    ok(s.cancelApplication(p, id(a)));
    bad(s.cancelApplication(p, id(a)));
    assertEquals(0, mine().size());
  }

  @Test
  void ratingChangeDelete() {
    String a = add(10);
    rate(e, a, 2);
    rate(e, a, 5);
    JsonObject row =
        ok(s.getRatedApplications(e)).getAsJsonArray("applications").get(0).getAsJsonObject();
    assertEquals(5, row.get("myScore").getAsInt());
    assertEquals(5, row.get("average").getAsInt());
    ok(s.deleteRating(e, id(a)));
    assertEquals(0, ok(s.getRatedApplications(e)).getAsJsonArray("applications").size());
    bad(s.deleteRating(e, id(a)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "6", "1.5", "\"5\"", "null"})
  void invalidScores(String score) {
    String a = add(10);
    bad(s.rateApplication(e, "{\"applicationId\":\"" + a + "\",\"score\":" + score + "}"));
  }

  @Test
  void directionsRestrictionAndNormalization() {
    add(10);
    ok(s.addApplications(p, "{\"applications\":[" + body(10, "физика") + "]}"));
    assertEquals(
        1,
        ok(s.searchApplications(e, "{\"directions\":[\" МАТЕМАТИКА \" ]}"))
            .getAsJsonArray("applications")
            .size());
    bad(s.searchApplications(e, "{\"directions\":[\"физика\"]}"));
    String a = mine().get(1).getAsJsonObject().get("id").getAsString();
    bad(s.rateApplication(e, "{\"applicationId\":\"" + a + "\",\"score\":5}"));
  }

  @Test
  void accountRemovalCascades() {
    String a = add(10);
    rate(e, a, 5);
    String old = login("part");
    ok(s.deleteAccount(p));
    bad(s.getOwnApplications(old));
    assertEquals(0, ok(s.getRatedApplications(e)).getAsJsonArray("applications").size());
    bad(s.login("{\"login\":\"part\",\"password\":\"password123\"}"));
  }

  @Test
  void expertRemovalDeletesVotes() {
    String a = add(10);
    rate(e, a, 5);
    ok(s.deleteAccount(e));
    assertFalse(mine().get(0).getAsJsonObject().has("average"));
  }

  @Test
  void allocationSkipsExpensiveAndStopsAtThreshold() {
    String big = add(120), small = add(60), low = add(10), unrated = add(1);
    rate(e, big, 5);
    rate(e, small, 4);
    rate(e, low, 3);
    JsonObject result = finish();
    assertEquals(1, result.getAsJsonArray("awards").size());
    assertEquals(
        small,
        result
            .getAsJsonArray("awards")
            .get(0)
            .getAsJsonObject()
            .get("applicationId")
            .getAsString());
    assertEquals(40, result.get("remainingFund").getAsInt());
  }

  @Test
  void equalScoresPreferSmallerAmount() {
    String large = add(70), small = add(40);
    rate(e, large, 5);
    rate(e, small, 5);
    JsonArray awards = finish().getAsJsonArray("awards");
    assertEquals(1, awards.size());
    assertEquals(small, awards.get(0).getAsJsonObject().get("applicationId").getAsString());
  }

  @Test
  void averageRatherThanSum() {
    String a = add(100), b = add(100);
    String e2 = expert("expert2", "Сидоров");
    rate(e, a, 5);
    rate(e2, a, 1);
    rate(e, b, 4);
    assertEquals(
        b,
        finish()
            .getAsJsonArray("awards")
            .get(0)
            .getAsJsonObject()
            .get("applicationId")
            .getAsString());
  }

  @Test
  void tiesAreFrozenAndBudgetConserved() {
    String a = add(60), b = add(60);
    rate(e, a, 5);
    rate(e, b, 5);
    JsonObject first = finish();
    assertEquals(first, ok(s.getResults(p)));
    assertEquals(40, first.get("remainingFund").getAsInt());
  }

  @Test
  void deadlineBoundaryAndFrozenState() {
    String a = add(10);
    rate(e, a, 5);
    bad(s.getResults(e));
    time.now = X;
    bad(s.addApplications(p, "{}"));
    bad(s.rateApplication(e, "{\"applicationId\":\"" + a + "\",\"score\":1}"));
    bad(s.deleteRating(e, id(a)));
    bad(s.cancelApplication(p, id(a)));
    bad(s.deleteAccount(p));
    bad(s.registerParticipant("{}"));
    ok(s.getResults(e));
    ok(s.logout(p));
    p = login("part");
    ok(s.getResults(p));
  }

  @Test
  void emptyCompetition() {
    assertEquals(0, finish().getAsJsonArray("awards").size());
  }

  @Test
  void separateServersDoNotShareUsers() {
    Server other = new Server(BigDecimal.ZERO, BigDecimal.ONE, X, time);
    bad(other.login("{\"login\":\"part\",\"password\":\"password123\"}"));
  }

  @Test
  void fractionalMoney() {
    ok(
        s.addApplications(
            p,
            "{\"applications\":[{\"title\":\"A\",\"description\":\"B\",\"directions\":[\"математика\"],\"amount\":0.01}]}"));
    String a = mine().get(0).getAsJsonObject().get("id").getAsString();
    rate(e, a, 5);
    assertEquals(new BigDecimal("99.99"), finish().get("remainingFund").getAsBigDecimal());
  }
}
