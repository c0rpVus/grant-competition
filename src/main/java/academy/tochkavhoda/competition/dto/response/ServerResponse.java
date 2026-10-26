package academy.tochkavhoda.competition.dto.response;

public class ServerResponse {
  private int responseCode;
  private String responseData;

  public ServerResponse(int code, String data) {
    responseCode = code;
    responseData = data;
  }

  public int getResponseCode() {
    return responseCode;
  }

  public String getResponseData() {
    return responseData;
  }

  public void setResponseCode(int code) {
    responseCode = code;
  }

  public void setResponseData(String data) {
    responseData = data;
  }
}
