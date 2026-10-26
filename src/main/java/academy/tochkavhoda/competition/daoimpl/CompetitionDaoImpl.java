package academy.tochkavhoda.competition.daoimpl;

import academy.tochkavhoda.competition.dao.CompetitionDao;
import academy.tochkavhoda.competition.database.Database;
import academy.tochkavhoda.competition.model.*;

public final class CompetitionDaoImpl implements CompetitionDao {
  private final Database.Store store;

  public CompetitionDaoImpl(CompetitionState state) {
    store = Database.getInstance().create(state);
  }

  public CompetitionState state() {
    return store.state();
  }

  public java.util.List<User> users() {
    return store.users();
  }

  public void addUser(User u) {
    store.addUser(u);
  }

  public User user(String id) {
    return store.user(id);
  }

  public User session(String token) {
    return store.session(token);
  }

  public void login(String token, String id) {
    store.login(token, id);
  }

  public void logout(String token) {
    store.logout(token);
  }

  public void deleteUser(String id) {
    store.deleteUser(id);
  }

  public java.util.List<Application> applications() {
    return store.applications();
  }

  public Application application(String id) {
    return store.application(id);
  }

  public void addApplication(Application a) {
    store.addApplication(a);
  }

  public void deleteApplication(String id) {
    store.deleteApplication(id);
  }

  public java.util.List<Rating> ratings() {
    return store.ratings();
  }

  public void rate(Rating r) {
    store.rate(r);
  }

  public void removeRating(String expert, String app) {
    store.removeRating(expert, app);
  }
}
