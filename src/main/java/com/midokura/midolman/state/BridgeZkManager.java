/*
 * @(#)BridgeZkManager        1.6 11/09/08
 *
 * Copyright 2011 Midokura KK
 */
package com.midokura.midolman.state;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.ZooDefs.Ids;

import com.midokura.midolman.state.GreZkManager.GreKey;
import com.midokura.midolman.state.PortDirectory.PortConfig;

/**
 * Class to manage the bridge ZooKeeper data.
 * 
 * @version 1.6 11 Sept 2011
 * @author Ryu Ishimoto
 */
public class BridgeZkManager extends ZkManager {

    private GreZkManager greZkManager = null;
    private PortZkManager portZkManager = null;

    public static class BridgeConfig {

        public BridgeConfig() {
            super();
        }

        public BridgeConfig(String name, UUID tenantId) {
            this(name, tenantId, -1);
        }

        public BridgeConfig(String name, UUID tenantId, int greKey) {
            super();
            this.name = name;
            this.tenantId = tenantId;
            this.greKey = greKey;
        }

        public String name;
        public UUID tenantId;
        public int greKey;
    }

    /**
     * Initializes a BridgeZkManager object with a ZooKeeper client and the root
     * path of the ZooKeeper directory.
     * 
     * @param zk
     *            ZooKeeper object.
     * @param basePath
     *            The root path.
     */
    public BridgeZkManager(Directory zk, String basePath) {
        super(zk, basePath);
        greZkManager = new GreZkManager(zk, basePath);
        portZkManager = new PortZkManager(zk, basePath);
    }

    public BridgeZkManager(ZooKeeper zk, String basePath) {
        this(new ZkDirectory(zk, "", null), basePath);
    }

    /**
     * Constructs a list of ZooKeeper update operations to perform when adding a
     * new bridge.
     * 
     * @param bridgeNode
     *            ZooKeeper node representing a key-value entry of Bridge UUID
     *            and BridgeConfig object.
     * @return A list of Op objects to represent the operations to perform.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public List<Op> prepareBridgeCreate(
            ZkNodeEntry<UUID, BridgeConfig> bridgeNode)
            throws ZkStateSerializationException, KeeperException,
            InterruptedException {

        // Create a new GRE key. Hide this from outside.
        ZkNodeEntry<Integer, GreKey> gre = greZkManager.createGreKey();
        bridgeNode.value.greKey = gre.key;

        List<Op> ops = new ArrayList<Op>();
        try {
            ops.add(Op.create(pathManager.getBridgePath(bridgeNode.key),
                    serialize(bridgeNode.value), Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT));
        } catch (IOException e) {
            throw new ZkStateSerializationException(
                    "Could not serialize BridgeConfig", e, BridgeConfig.class);
        }
        ops.add(Op.create(pathManager.getTenantBridgePath(
                bridgeNode.value.tenantId, bridgeNode.key), null,
                Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));
        ops.add(Op.create(pathManager.getBridgePortsPath(bridgeNode.key), null,
                Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT));

        // Update GreKey to reference the bridge.
        gre.value.bridgeId = bridgeNode.key;
        ops.addAll(greZkManager.prepareGreUpdate(gre));
        return ops;
    }

    /**
     * Performs an atomic update on the ZooKeeper to add a new bridge entry.
     * 
     * @param bridge
     *            Bridge object to add to the ZooKeeper directory.
     * @return The UUID of the newly created object.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public UUID create(BridgeConfig bridge) throws InterruptedException,
            KeeperException, ZkStateSerializationException {
        UUID id = UUID.randomUUID();
        ZkNodeEntry<UUID, BridgeConfig> bridgeNode = new ZkNodeEntry<UUID, BridgeConfig>(
                id, bridge);
        zk.multi(prepareBridgeCreate(bridgeNode));
        return id;
    }

    /**
     * Gets a ZooKeeper node entry key-value pair of a bridge with the given ID.
     * 
     * @param id
     *            The ID of the bridge.
     * @return Bridge object found.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public ZkNodeEntry<UUID, BridgeConfig> get(UUID id) throws KeeperException,
            InterruptedException, ZkStateSerializationException {
        return get(id, null);
    }

    /**
     * Gets a ZooKeeper node entry key-value pair of a bridge with the given ID
     * and sets a watcher on the node.
     * 
     * @param id
     *            The ID of the bridge.
     * @param watcher
     *            The watcher that gets notified when there is a change in the
     *            node.
     * @return Route object found.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public ZkNodeEntry<UUID, BridgeConfig> get(UUID id, Runnable watcher)
            throws KeeperException, InterruptedException,
            ZkStateSerializationException {
        byte[] data = zk.get(pathManager.getBridgePath(id), watcher);
        BridgeConfig config = null;
        try {
            config = deserialize(data, BridgeConfig.class);
        } catch (IOException e) {
            throw new ZkStateSerializationException(
                    "Could not deserialize bridge " + id + " to BridgeConfig",
                    e, BridgeConfig.class);
        }
        return new ZkNodeEntry<UUID, BridgeConfig>(id, config);
    }

    /**
     * Gets a list of ZooKeeper bridge nodes belonging to a tenant with the
     * given ID.
     * 
     * @param tenantId
     *            The ID of the tenant to find the bridges of.
     * @return A list of ZooKeeper route nodes.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public List<ZkNodeEntry<UUID, BridgeConfig>> list(UUID tenantId)
            throws KeeperException, InterruptedException,
            ZkStateSerializationException {
        return list(tenantId, null);
    }

    /**
     * Gets a list of ZooKeeper bridge nodes belonging to a tenant with the
     * given ID.
     * 
     * @param tenantId
     *            The ID of the tenant to find the bridges of.
     * @param watcher
     *            The watcher to set on the changes to the bridges for this
     *            tenant.
     * @return A list of ZooKeeper bridge nodes.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public List<ZkNodeEntry<UUID, BridgeConfig>> list(UUID tenantId,
            Runnable watcher) throws KeeperException, InterruptedException,
            ZkStateSerializationException {
        List<ZkNodeEntry<UUID, BridgeConfig>> result = new ArrayList<ZkNodeEntry<UUID, BridgeConfig>>();
        Set<String> bridgeIds = zk.getChildren(pathManager
                .getTenantBridgesPath(tenantId), watcher);
        for (String bridgeId : bridgeIds) {
            // For now, get each one.
            result.add(get(UUID.fromString(bridgeId)));
        }
        return result;
    }

    /**
     * Updates the BridgeConfig values with the given BridgeConfig object.
     * 
     * @param entry
     *            BridgeConfig object to save.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public void update(ZkNodeEntry<UUID, BridgeConfig> entry)
            throws KeeperException, InterruptedException,
            ZkStateSerializationException {
        // Update any version for now.
        try {
            zk.update(pathManager.getBridgePath(entry.key),
                    serialize(entry.value));
        } catch (IOException e) {
            throw new ZkStateSerializationException(
                    "Could not serialize bridge " + entry.key
                            + " to BridgeConfig", e, BridgeConfig.class);
        }
    }

    /**
     * Constructs a list of operations to perform in a bridge deletion.
     * 
     * @param entry
     *            Bridge ZooKeeper entry to delete.
     * @return A list of Op objects representing the operations to perform.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public List<Op> prepareBridgeDelete(ZkNodeEntry<UUID, BridgeConfig> entry)
            throws KeeperException, InterruptedException,
            ZkStateSerializationException, IOException {
        List<Op> ops = new ArrayList<Op>();

        // Delete the ports
        List<ZkNodeEntry<UUID, PortConfig>> portEntries = portZkManager
                .listBridgePorts(entry.key);
        for (ZkNodeEntry<UUID, PortConfig> portEntry : portEntries) {
            ops.addAll(portZkManager.prepareBridgePortDelete(portEntry));
        }
        ops.add(Op.delete(pathManager.getBridgePortsPath(entry.key), -1));

        // Delete GRE
        GreKey gre = new GreKey(entry.key);
        ops.addAll(greZkManager
                .prepareGreDelete(new ZkNodeEntry<Integer, GreKey>(
                        entry.value.greKey, gre)));

        // Delete the tenant bridge entry
        ops.add(Op.delete(pathManager.getTenantBridgePath(entry.value.tenantId,
                entry.key), -1));

        // Delete the bridge
        ops.add(Op.delete(pathManager.getBridgePath(entry.key), -1));
        return ops;
    }

    /***
     * Deletes a bridge and its related data from the ZooKeeper directories
     * atomically.
     * 
     * @param id
     *            ID of the bridge to delete.
     * @throws ZkStateSerializationException
     *             Serialization error occurred.
     * @throws KeeperException
     *             ZooKeeper error occurred.
     * @throws InterruptedException
     *             ZooKeeper was unresponsive.
     */
    public void delete(UUID id) throws InterruptedException, KeeperException,
            ZkStateSerializationException, IOException {
        this.zk.multi(prepareBridgeDelete(get(id)));
    }
}
