/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.timelock.partition;

import java.util.List;

public class NopTimeLockPartitioner implements TimeLockPartitioner {
    @Override
    public Assignment partition(List<String> clients, List<String> hosts, long seed) {
        Assignment.Builder builder = Assignment.builder();
        clients.forEach(client -> hosts.forEach(host -> builder.addMapping(client, host)));
        return builder.build();
    }
}