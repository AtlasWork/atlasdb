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

package com.palantir.atlasdb.keyvalue.cassandra;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.cassandra.thrift.CASResult;
import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.ColumnOrSuperColumn;
import org.apache.cassandra.thrift.Compression;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.CqlResult;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.KeyRange;
import org.apache.cassandra.thrift.KeySlice;
import org.apache.cassandra.thrift.Mutation;
import org.apache.cassandra.thrift.NotFoundException;
import org.apache.cassandra.thrift.SchemaDisagreementException;
import org.apache.cassandra.thrift.SlicePredicate;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.qos.QosClient;

@SuppressWarnings({"all"}) // thrift variable names.
public class QosCassandraClient implements CassandraClient {

    private static final int DEFAULT_ESTIMATED_READ_BYTES = 100;

    private static final Logger log = LoggerFactory.getLogger(CassandraClient.class);

    private final CassandraClient client;
    private final QosClient qosClient;

    public QosCassandraClient(CassandraClient client, QosClient qosClient) {
        this.client = client;
        this.qosClient = qosClient;
    }

    @Override
    public Cassandra.Client rawClient() {
        return client.rawClient();
    }

    @Override
    public Map<ByteBuffer, List<ColumnOrSuperColumn>> multiget_slice(String kvsMethodName, TableReference tableRef,
            List<ByteBuffer> keys, SlicePredicate predicate, ConsistencyLevel consistency_level)
            throws InvalidRequestException, UnavailableException, TimedOutException, TException {
        return qosClient.executeRead(
                () -> DEFAULT_ESTIMATED_READ_BYTES,
                () -> client.multiget_slice(kvsMethodName, tableRef, keys,
                predicate, consistency_level),
                this::getApproximateReadByteCount);
    }

    private int getApproximateReadByteCount(Map<ByteBuffer, List<ColumnOrSuperColumn>> result) {
        return getCollectionSize(result.entrySet(),
                rowResult -> ThriftObjectSizeUtils.getByteBufferSize(rowResult.getKey())
                        + getCollectionSize(rowResult.getValue(),
                        ThriftObjectSizeUtils::getColumnOrSuperColumnSize));
    }

    @Override
    public List<KeySlice> get_range_slices(String kvsMethodName, TableReference tableRef, SlicePredicate predicate,
            KeyRange range, ConsistencyLevel consistency_level)
            throws InvalidRequestException, UnavailableException, TimedOutException, TException {
        return qosClient.executeRead(
                () -> DEFAULT_ESTIMATED_READ_BYTES,
                () -> client.get_range_slices(kvsMethodName, tableRef, predicate, range, consistency_level),
                result -> getCollectionSize(result, ThriftObjectSizeUtils::getKeySliceSize));
    }

    @Override
    public void batch_mutate(String kvsMethodName, Map<ByteBuffer, Map<String, List<Mutation>>> mutation_map,
            ConsistencyLevel consistency_level)
            throws InvalidRequestException, UnavailableException, TimedOutException, TException {
        qosClient.executeWrite(
                () -> getApproximateWriteByteCount(mutation_map),
                () -> client.batch_mutate(kvsMethodName, mutation_map, consistency_level));
    }

    private int getApproximateWriteByteCount(Map<ByteBuffer, Map<String, List<Mutation>>> batchMutateMap) {
        int approxBytesForKeys = getCollectionSize(batchMutateMap.keySet(), ThriftObjectSizeUtils::getByteBufferSize);
        int approxBytesForValues = getCollectionSize(batchMutateMap.values(), currentMap ->
                getCollectionSize(currentMap.keySet(), ThriftObjectSizeUtils::getStringSize)
                        + getCollectionSize(currentMap.values(),
                        mutations -> getCollectionSize(mutations, ThriftObjectSizeUtils::getMutationSize)));
        return approxBytesForKeys + approxBytesForValues;
    }

    @Override
    public ColumnOrSuperColumn get(TableReference tableReference, ByteBuffer key, byte[] column,
            ConsistencyLevel consistency_level)
            throws InvalidRequestException, NotFoundException, UnavailableException, TimedOutException, TException {
        return qosClient.executeRead(
                () -> DEFAULT_ESTIMATED_READ_BYTES,
                () -> client.get(tableReference, key, column, consistency_level),
                ThriftObjectSizeUtils::getColumnOrSuperColumnSize);
    }

    @Override
    public CASResult cas(TableReference tableReference, ByteBuffer key, List<Column> expected, List<Column> updates,
            ConsistencyLevel serial_consistency_level, ConsistencyLevel commit_consistency_level)
            throws InvalidRequestException, UnavailableException, TimedOutException, TException {
        // TODO(nziebart): should this be considered as a write or do we need to treat is as both read and write?
        return client.cas(tableReference, key, expected, updates, serial_consistency_level,
                commit_consistency_level);
    }

    @Override
    public CqlResult execute_cql3_query(CqlQuery cqlQuery, Compression compression, ConsistencyLevel consistency)
            throws InvalidRequestException, UnavailableException, TimedOutException, SchemaDisagreementException,
            TException {

        return qosClient.executeRead(
                () -> DEFAULT_ESTIMATED_READ_BYTES,
                () -> client.execute_cql3_query(cqlQuery, compression, consistency),
                ThriftObjectSizeUtils::getCqlResultSize);
    }

    private <T> int getCollectionSize(Collection<T> collection, Function<T, Integer> singleObjectSizeFunction) {
        return ThriftObjectSizeUtils.getCollectionSize(collection, singleObjectSizeFunction);
    }
}
