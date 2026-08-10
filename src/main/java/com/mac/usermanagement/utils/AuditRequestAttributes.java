package com.mac.usermanagement.utils;

public final class AuditRequestAttributes {

    public static final String ACTOR_ID = AuditRequestAttributes.class.getName() + ".actorId";
    public static final String ACTOR_NAME = AuditRequestAttributes.class.getName() + ".actorName";
    public static final String TENANT_ID = AuditRequestAttributes.class.getName() + ".tenantId";

    private AuditRequestAttributes() {}
}
