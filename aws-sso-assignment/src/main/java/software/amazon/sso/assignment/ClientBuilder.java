package software.amazon.sso.assignment;

import software.amazon.awssdk.services.ssoadmin.SsoAdminClient;
import software.amazon.cloudformation.LambdaWrapper;

public class ClientBuilder {
  public static SsoAdminClient getClient() {
    return SsoAdminClient.builder()
            .httpClient(LambdaWrapper.HTTP_CLIENT)
            .build();
  }
}
