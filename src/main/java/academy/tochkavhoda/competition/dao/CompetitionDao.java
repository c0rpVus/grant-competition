package academy.tochkavhoda.competition.dao;

import academy.tochkavhoda.competition.model.*;

public interface CompetitionDao {
  CompetitionState state();

  java.util.List<User> users();

  void addUser(User u);

  User user(String id);

  User session(String token);

  void login(String token, String id);

  void logout(String token);

  void deleteUser(String id);

  java.util.List<Application> applications();

  Application application(String id);

  void addApplication(Application a);

  void deleteApplication(String id);

  java.util.List<Rating> ratings();

  void rate(Rating r);

  void removeRating(String expert, String app);
}
