package academy.tochkavhoda.competition.service;

import academy.tochkavhoda.competition.dao.*;
import academy.tochkavhoda.competition.daoimpl.*;
import academy.tochkavhoda.competition.dto.request.*;
import academy.tochkavhoda.competition.dto.response.*;
import academy.tochkavhoda.competition.exception.*;
import academy.tochkavhoda.competition.model.*;
import com.google.gson.*;
import java.math.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Coordinates business operations; persistence is accessed only through DAO. */
public final class CompetitionService {
  private final CompetitionDao dao;
  private final Clock clock;
  private final Gson gson = new GsonBuilder().setStrictness(Strictness.STRICT).create();
  private ResultsResponse results;

  public CompetitionService(BigDecimal fund, BigDecimal threshold, Instant deadline, Clock clock) {
    if (fund == null
        || fund.signum() < 0
        || fund.scale() > 2
        || threshold == null
        || threshold.compareTo(BigDecimal.ONE) < 0
        || threshold.compareTo(BigDecimal.valueOf(5)) > 0
        || deadline == null
        || clock == null) throw new IllegalArgumentException("Invalid competition configuration");
    this.clock = clock;
    dao = new CompetitionDaoImpl(new CompetitionState(fund, threshold, deadline));
  }

  private void require(boolean ok, String message) {
    if (!ok) throw new CompetitionException("INVALID_OPERATION", message);
  }

  private ServerResponse execute(Supplier<Object> action) {
    try {
      return new ServerResponse(200, gson.toJson(action.get()));
    } catch (CompetitionException e) {
      return new ServerResponse(400, gson.toJson(new ErrorResponse(e.getMessage())));
    } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
      return new ServerResponse(
          400, gson.toJson(new ErrorResponse("Некорректный JSON или тип поля")));
    }
  }

  private <T> T read(String json, Class<T> type) {
    require(json != null, "Запрос обязателен");
    JsonElement tree = gson.fromJson(json, JsonElement.class);
    require(tree != null && tree.isJsonObject(), "Ожидается JSON-объект");
    validateTypes(tree, type);
    T value = gson.fromJson(tree, type);
    require(value != null, "Пустой запрос");
    return value;
  }

  private void validateTypes(JsonElement element, Class<?> type) {
    if (!element.isJsonObject()) throw new JsonParseException("Object required");
    for (var field : type.getRecordComponents()) {
      JsonElement v = element.getAsJsonObject().get(field.getName());
      if (v == null || v.isJsonNull()) continue;
      Class<?> t = field.getType();
      if (t == String.class)
        require(
            v.isJsonPrimitive() && v.getAsJsonPrimitive().isString(),
            "Ожидается строка: " + field.getName());
      else if (t == Integer.class || t == BigDecimal.class) {
        require(
            v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber(),
            "Ожидается число: " + field.getName());
        if (t == Integer.class)
          try {
            v.getAsBigDecimal().intValueExact();
          } catch (ArithmeticException ex) {
            throw new JsonParseException("Integer required");
          }
      } else if (Set.class.isAssignableFrom(t)) {
        require(v.isJsonArray(), "Ожидается массив направлений");
        for (JsonElement x : v.getAsJsonArray())
          require(
              x.isJsonPrimitive() && x.getAsJsonPrimitive().isString(),
              "Направление должно быть строкой");
      } else if (List.class.isAssignableFrom(t)) {
        require(v.isJsonArray(), "Ожидается массив заявок");
        for (JsonElement x : v.getAsJsonArray()) validateTypes(x, ApplicationRequest.class);
      }
    }
  }

  private String text(String value, int min, int max, String name) {
    require(
        value != null && value.strip().length() >= min && value.strip().length() <= max,
        "Некорректное поле: " + name);
    return value.strip();
  }

  private Set<String> directions(Set<String> values) {
    require(
        values != null && !values.isEmpty() && values.size() <= 30,
        "Укажите от 1 до 30 направлений");
    Set<String> out = new TreeSet<>();
    for (String s : values) out.add(text(s, 1, 100, "direction").toLowerCase(Locale.ROOT));
    return Set.copyOf(out);
  }

  private void open() {
    require(clock.instant().isBefore(dao.state().deadline()), "Конкурс завершён");
  }

  private User user(String token) {
    User u = token == null ? null : dao.session(token);
    require(u != null, "Недействительный токен");
    return u;
  }

  private User role(String token, String role) {
    User u = user(token);
    require(u.role().equals(role), "Операция недоступна для вашей роли");
    return u;
  }

  private String hash(String password, String salt) {
    try {
      return Base64.getEncoder()
          .encodeToString(
              SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                  .generateSecret(
                      new PBEKeySpec(
                          password.toCharArray(), Base64.getDecoder().decode(salt), 60000, 256))
                  .getEncoded());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private Object register(
      String first,
      String last,
      String company,
      Set<String> dirs,
      String login,
      String password,
      String role) {
    open();
    first = text(first, 1, 100, "firstName");
    last = text(last, 1, 100, "lastName");
    login = text(login, 3, 64, "login");
    require(login.matches("[A-Za-z0-9_.-]+"), "Логин: латиница, цифры, _, ., -");
    require(
        password != null
            && password.length() >= 8
            && password.length() <= 128
            && !password.isBlank(),
        "Пароль должен содержать от 8 до 128 символов");
    if (role.equals("PARTICIPANT")) company = text(company, 1, 200, "company");
    else dirs = directions(dirs);
    for (User u : dao.users()) {
      require(!u.login().equalsIgnoreCase(login), "Логин уже используется");
      if (role.equals("PARTICIPANT") && u.company() != null)
        require(!u.company().equalsIgnoreCase(company), "Фирма уже зарегистрирована");
      if (role.equals("EXPERT") && u.role().equals(role))
        require(
            !(u.firstName().equalsIgnoreCase(first) && u.lastName().equalsIgnoreCase(last)),
            "Эксперт уже зарегистрирован");
    }
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    String s = Base64.getEncoder().encodeToString(salt);
    dao.addUser(
        new User(
            UUID.randomUUID().toString(),
            first,
            last,
            company,
            dirs,
            login,
            hash(password, s),
            s,
            role));
    return Map.of();
  }

  public synchronized ServerResponse registerParticipant(String json) {
    return execute(
        () -> {
          var r = read(json, RegisterParticipantRequest.class);
          return register(
              r.firstName(),
              r.lastName(),
              r.company(),
              null,
              r.login(),
              r.password(),
              "PARTICIPANT");
        });
  }

  public synchronized ServerResponse registerExpert(String json) {
    return execute(
        () -> {
          var r = read(json, RegisterExpertRequest.class);
          return register(
              r.firstName(), r.lastName(), null, r.directions(), r.login(), r.password(), "EXPERT");
        });
  }

  public synchronized ServerResponse login(String json) {
    return execute(
        () -> {
          var r = read(json, LoginRequest.class);
          require(r.login() != null && r.password() != null, "Укажите логин и пароль");
          User u =
              dao.users().stream()
                  .filter(x -> x.login().equalsIgnoreCase(r.login()))
                  .findFirst()
                  .orElse(null);
          require(
              u != null
                  && MessageDigest.isEqual(
                      hash(r.password(), u.salt())
                          .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                      u.passwordHash().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
              "Неверный логин или пароль");
          String token = UUID.randomUUID().toString();
          dao.login(token, u.id());
          return new LoginResponse(token);
        });
  }

  public synchronized ServerResponse logout(String token) {
    return execute(
        () -> {
          user(token);
          dao.logout(token);
          return Map.of();
        });
  }

  public synchronized ServerResponse deleteAccount(String token) {
    return execute(
        () -> {
          User u = user(token);
          open();
          dao.deleteUser(u.id());
          return Map.of();
        });
  }

  public synchronized ServerResponse addApplications(String token, String json) {
    return execute(
        () -> {
          User u = role(token, "PARTICIPANT");
          open();
          var r = read(json, AddApplicationsRequest.class);
          require(
              r.applications() != null
                  && !r.applications().isEmpty()
                  && r.applications().size() <= 100,
              "Укажите от 1 до 100 заявок");
          List<Application> apps = new ArrayList<>();
          for (var a : r.applications()) {
            require(a != null, "Пустая заявка");
            String title = text(a.title(), 1, 200, "title"),
                description = text(a.description(), 1, 10000, "description");
            Set<String> dirs = directions(a.directions());
            require(
                a.amount() != null && a.amount().signum() > 0 && a.amount().scale() <= 2,
                "Сумма должна быть положительной, не более 2 знаков после точки");
            apps.add(
                new Application(
                    UUID.randomUUID().toString(), u.id(), title, description, dirs, a.amount()));
          }
          apps.forEach(dao::addApplication);
          return new IdsResponse(apps.stream().map(Application::id).toList());
        });
  }

  private Application app(String id) {
    Application a = dao.application(id);
    require(a != null, "Заявка не найдена");
    return a;
  }

  public synchronized ServerResponse cancelApplication(String token, String json) {
    return execute(
        () -> {
          User u = role(token, "PARTICIPANT");
          open();
          Application a = app(read(json, IdRequest.class).applicationId());
          require(a.ownerId().equals(u.id()), "Можно отменить только свою заявку");
          dao.deleteApplication(a.id());
          return Map.of();
        });
  }

  private void expertise(User u, Application a) {
    require(
        !Collections.disjoint(u.directions(), a.directions()), "Заявка вне направлений эксперта");
  }

  public synchronized ServerResponse rateApplication(String token, String json) {
    return execute(
        () -> {
          User u = role(token, "EXPERT");
          open();
          var r = read(json, RatingRequest.class);
          Application a = app(r.applicationId());
          expertise(u, a);
          require(
              r.score() != null && r.score() >= 1 && r.score() <= 5,
              "Оценка должна быть от 1 до 5");
          dao.rate(new Rating(u.id(), a.id(), r.score()));
          return Map.of();
        });
  }

  public synchronized ServerResponse deleteRating(String token, String json) {
    return execute(
        () -> {
          User u = role(token, "EXPERT");
          open();
          Application a = app(read(json, IdRequest.class).applicationId());
          expertise(u, a);
          require(
              dao.ratings().stream()
                  .anyMatch(r -> r.expertId().equals(u.id()) && r.applicationId().equals(a.id())),
              "Оценка не найдена");
          dao.removeRating(u.id(), a.id());
          return Map.of();
        });
  }

  private long sum(Application a) {
    return dao.ratings().stream()
        .filter(r -> r.applicationId().equals(a.id()))
        .mapToLong(Rating::score)
        .sum();
  }

  private long count(Application a) {
    return dao.ratings().stream().filter(r -> r.applicationId().equals(a.id())).count();
  }

  private BigDecimal average(Application a) {
    long n = count(a);
    return n == 0
        ? null
        : BigDecimal.valueOf(sum(a))
            .divide(BigDecimal.valueOf(n), 12, RoundingMode.HALF_UP)
            .stripTrailingZeros();
  }

  private ApplicationResponse view(Application a, User u) {
    Integer score =
        dao.ratings().stream()
            .filter(r -> r.applicationId().equals(a.id()) && r.expertId().equals(u.id()))
            .map(Rating::score)
            .findFirst()
            .orElse(null);
    return new ApplicationResponse(
        a.id(),
        a.ownerId(),
        a.title(),
        a.description(),
        a.directions(),
        a.amount(),
        average(a),
        score);
  }

  public synchronized ServerResponse getOwnApplications(String token) {
    return execute(
        () -> {
          User u = role(token, "PARTICIPANT");
          return new ApplicationsResponse(
              dao.applications().stream()
                  .filter(a -> a.ownerId().equals(u.id()))
                  .map(a -> view(a, u))
                  .toList());
        });
  }

  public synchronized ServerResponse searchApplications(String token, String json) {
    return execute(
        () -> {
          User u = role(token, "EXPERT");
          Set<String> dirs = directions(read(json, SearchRequest.class).directions());
          require(u.directions().containsAll(dirs), "Поиск вне направлений эксперта");
          return new ApplicationsResponse(
              dao.applications().stream()
                  .filter(a -> !Collections.disjoint(a.directions(), dirs))
                  .map(a -> view(a, u))
                  .toList());
        });
  }

  public synchronized ServerResponse getRatedApplications(String token) {
    return execute(
        () -> {
          User u = role(token, "EXPERT");
          return new ApplicationsResponse(
              dao.applications().stream()
                  .filter(
                      a ->
                          dao.ratings().stream()
                              .anyMatch(
                                  r ->
                                      r.applicationId().equals(a.id())
                                          && r.expertId().equals(u.id())))
                  .map(a -> view(a, u))
                  .toList());
        });
  }

  public synchronized ServerResponse getResults(String token) {
    return execute(
        () -> {
          user(token);
          require(
              !clock.instant().isBefore(dao.state().deadline()),
              "Итоги доступны после завершения конкурса");
          if (results == null) {
            List<Application> ranked =
                new ArrayList<>(dao.applications().stream().filter(a -> count(a) > 0).toList());
            Collections.shuffle(ranked);
            ranked.sort(
                (a, b) -> {
                  int c =
                      BigDecimal.valueOf(sum(b))
                          .multiply(BigDecimal.valueOf(count(a)))
                          .compareTo(
                              BigDecimal.valueOf(sum(a)).multiply(BigDecimal.valueOf(count(b))));
                  return c != 0 ? c : a.amount().compareTo(b.amount());
                });
            BigDecimal remaining = dao.state().fund();
            List<AwardResponse> awards = new ArrayList<>();
            for (Application a : ranked) {
              if (BigDecimal.valueOf(sum(a))
                      .compareTo(dao.state().threshold().multiply(BigDecimal.valueOf(count(a))))
                  <= 0) break;
              if (a.amount().compareTo(remaining) <= 0) {
                awards.add(new AwardResponse(a.id(), a.amount(), average(a)));
                remaining = remaining.subtract(a.amount());
              }
            }
            results = new ResultsResponse(List.copyOf(awards), remaining);
          }
          return results;
        });
  }
}
