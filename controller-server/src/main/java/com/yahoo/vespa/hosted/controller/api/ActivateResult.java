// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api;

import com.yahoo.vespa.hosted.controller.api.identifiers.RevisionId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;

/**
 * @author Oyvind Gronnesby
 */
public class ActivateResult {
    // TODO jvenstad: Replace this class with just the PrepareResponse.

    private final RevisionId revisionId;
    private final PrepareResponse prepareResponse;
    private final long applicationZipSizeBytes;
    private final double quotaUsageRate;

    public ActivateResult(RevisionId revisionId, PrepareResponse prepareResponse, long applicationZipSizeBytes, double quotaUsageRate) {
        this.revisionId = revisionId;
        this.prepareResponse = prepareResponse;
        this.applicationZipSizeBytes = applicationZipSizeBytes;
        this.quotaUsageRate = quotaUsageRate;
    }

    public long applicationZipSizeBytes() {
        return applicationZipSizeBytes;
    }

    public RevisionId revisionId() {
        return revisionId;
    }

    public PrepareResponse prepareResponse() {
        return prepareResponse;
    }

    public double quotaUsageRate() {
        return quotaUsageRate;
    }

}
