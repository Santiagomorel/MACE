package com.company.rotations.decision.domain;

import com.company.rotations.models.Severidad;

import java.util.Map;
import java.util.HashMap;

public class DiscoveredPermissions {

    private final String tenantId;
    private final boolean s3FullAccess;
    private final boolean s3ReadOnly;
    private final boolean iamModify;
    private final boolean ec2InstanceControl;
    private final boolean ec2ReadOnly;
    private final boolean cloudWatchRead;
    private final boolean nothingActive;

    public DiscoveredPermissions(String tenantId, boolean s3FullAccess, boolean s3ReadOnly,
                                  boolean iamModify, boolean ec2InstanceControl,
                                  boolean ec2ReadOnly, boolean cloudWatchRead,
                                  boolean nothingActive) {
        this.tenantId = tenantId;
        this.s3FullAccess = s3FullAccess;
        this.s3ReadOnly = s3ReadOnly;
        this.iamModify = iamModify;
        this.ec2InstanceControl = ec2InstanceControl;
        this.ec2ReadOnly = ec2ReadOnly;
        this.cloudWatchRead = cloudWatchRead;
        this.nothingActive = nothingActive;
    }

    public String getTenantId() { return tenantId; }
    public boolean isS3FullAccess() { return s3FullAccess; }
    public boolean isS3ReadOnly() { return s3ReadOnly; }
    public boolean isIamModify() { return iamModify; }
    public boolean isEc2InstanceControl() { return ec2InstanceControl; }
    public boolean isEc2ReadOnly() { return ec2ReadOnly; }
    public boolean isCloudWatchRead() { return cloudWatchRead; }
    public boolean isNothingActive() { return nothingActive; }

    public Map<String, Severidad> toSeverityFloor() {
        Map<String, Severidad> floor = new HashMap<>();
        if (s3FullAccess) floor.put("s3_full_access", Severidad.CRITICO);
        if (s3ReadOnly) floor.put("s3_read_only", Severidad.ALTO);
        if (iamModify) floor.put("iam_modify", Severidad.CRITICO);
        if (ec2InstanceControl) floor.put("ec2_instance_control", Severidad.CRITICO);
        if (ec2ReadOnly) floor.put("ec2_read_only", Severidad.MEDIA);
        if (cloudWatchRead) floor.put("cloudwatch_read", Severidad.MEDIA);
        if (nothingActive) floor.put("nothing_active", Severidad.BAJO);
        return floor;
    }

    public String getHighestPermission() {
        if (s3FullAccess || iamModify || ec2InstanceControl) return "CRITICO";
        if (s3ReadOnly) return "ALTO";
        if (ec2ReadOnly || cloudWatchRead) return "MEDIA";
        return "BAJO";
    }
}
