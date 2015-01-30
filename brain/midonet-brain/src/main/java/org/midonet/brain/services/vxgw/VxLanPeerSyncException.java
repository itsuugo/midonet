/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.midonet.brain.services.vxgw;

import org.opendaylight.controller.sal.utils.StatusCode;

/**
 * An update to a VxLanPeer could not be applied.
 */
public class VxLanPeerSyncException extends RuntimeException {
    private static final long serialVersionUID = -1;
    private final MacLocation change;
    public final StatusCode statusCode;

    public VxLanPeerSyncException(String msg, MacLocation change,
                                  StatusCode statusCode) {
        super(msg);
        this.change = change;
        this.statusCode = statusCode;
    }

    public VxLanPeerSyncException(String msg, MacLocation change,
                                  Throwable cause) {
        super(msg, cause);
        this.change = change;
        this.statusCode = null;
    }

    @Override
    public String toString() {
        return String.format("Failed to apply %s, %s",
                             change.toString(), this.getMessage());
    }
}
