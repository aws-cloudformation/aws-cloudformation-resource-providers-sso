package software.amazon.sso.permissionset.utils;

public class Constants {
    public final static String MANAGED_POLICIES_LIMIT_EXCEED_MESSAGE = "You have exceeded AWS SSO limits. Cannot attach more than 20 managed policies. "
            + "Please refer to https://docs.aws.amazon.com/singlesignon/latest/userguide/limits.html.";
    public final static int RETRY_ATTEMPTS = 5;
    public final static int RETRY_ATTEMPTS_ZERO = 0;
    public final static String FAILED_WORKFLOW_REQUEST = "Request %s failed due to: %s";
}
