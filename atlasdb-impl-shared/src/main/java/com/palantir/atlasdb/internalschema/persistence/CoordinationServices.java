/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.atlasdb.internalschema.persistence;

import com.palantir.atlasdb.coordination.CoordinationService;
import com.palantir.atlasdb.coordination.TransformingCoordinationService;
import com.palantir.atlasdb.internalschema.InternalSchemaMetadata;

public final class CoordinationServices {
    private CoordinationServices() {
        // factory
    }

    public static CoordinationService<InternalSchemaMetadata> wrapHidingVersionSerialization(
            CoordinationService<VersionedInternalSchemaMetadata> rawCoordinationService) {
        return new TransformingCoordinationService<>(
                rawCoordinationService,
                InternalSchemaMetadataPayloadCodec::decode,
                InternalSchemaMetadataPayloadCodec::encode);
    }
}
