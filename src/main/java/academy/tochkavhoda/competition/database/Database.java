package academy.tochkavhoda.competition.database;

import academy.tochkavhoda.competition.model.*;
import java.util.*;

public final class Database {
  private static final Database INSTANCE = new Database();
  private final Map<String, Store> stores = new HashMap<>();

  private Database() {}

  public static Database getInstance() {
    return INSTANCE;
  }

  public synchronized Store create(CompetitionState state) {
    Store s = new Store(state);
    stores.put(UUID.randomUUID().toString(), s);
    return s;
  }

  public static final class Store {
    private final CompetitionState state;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, String> sessions = new HashMap<>();
    private final Map<String, Application> apps = new LinkedHashMap<>();
    private final Map<String, Rating> ratings = new HashMap<>();

    private Store(CompetitionState state) {
      this.state = state;
    }

    public CompetitionState state() {
      return state;
    }

    public List<User> users() {
      return List.copyOf(users.values());
    }

    public void addUser(User u) {
      users.put(u.id(), u);
    }

    public User user(String id) {
      return users.get(id);
    }

    public User session(String token) {
      return users.get(sessions.get(token));
    }

    public void login(String token, String id) {
      sessions.put(token, id);
    }

    public void logout(String token) {
      sessions.remove(token);
    }

    public void deleteUser(String id) {
      users.remove(id);
      sessions.values().removeIf(id::equals);
      for (Application a : applications()) if (a.ownerId().equals(id)) deleteApplication(a.id());
      ratings.values().removeIf(r -> r.expertId().equals(id));
    }

    public List<Application> applications() {
      return List.copyOf(apps.values());
    }

    public Application application(String id) {
      return apps.get(id);
    }

    public void addApplication(Application a) {
      apps.put(a.id(), a);
    }

    public void deleteApplication(String id) {
      apps.remove(id);
      ratings.values().removeIf(r -> r.applicationId().equals(id));
    }

    public List<Rating> ratings() {
      return List.copyOf(ratings.values());
    }

    public void rate(Rating r) {
      ratings.put(r.expertId() + ":" + r.applicationId(), r);
    }

    public void removeRating(String expert, String app) {
      ratings.remove(expert + ":" + app);
    }
  }
}
