package software.amazon.sso.permissionset;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class TestConstants {
    public final static String TEST_PERMISSION_SET_NAME = "testPermissionSet";
    public final static String TEST_PERMISSION_SET_DESCRIPTION = "This is permission set for test";
    public final static String TEST_SSO_INSTANCE_ARN = "arn:aws:sso:::instance/ins-1a5c249c9a03b908";
    public final static String TEST_SESSION_DURATION = "test session duration";
    public final static String TEST_RELAY_STATE = "test session duration";
    public final static String TEST_PERMISSION_SET_ARN = "arn:aws:sso:::permissionSet/ssoins-1a5c249c9a03b908/ps-d1fc7a84aead19b9";
    public final static String TEST_CONFLICT_EXCEPTION_MESSAGE = "Conflict exception detected";
    public final static String TEST_ADMIN_MANAGED_POLICY = "arn:aws:iam::aws:policy/AdministratorAccess";
    public final static String TEST_READONLY_POLICY = "arn:aws:iam::aws:policy/ReadOnly";
    public final static String TEST_INLINE_POLICY = "Inline policy";
    public final static String TEST_INLINE_POLICY_2 = "Inline policy2";
    public final static String THROTTLING_MESSAGE = "Operation Throttled.";
    public final static String ISE_MESSAGE = "There is an internal failure.";
    public static final Map<String, Object> SAMPLE_DOCUMENT_CONTENT = ImmutableMap.of(
            "schemaVersion", "1.2",
            "description", "Join instances to an AWS Directory Service domain."
    );
}
