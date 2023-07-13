package dev.imabad.theatrical.net;

import dev.architectury.networking.simple.MessageType;
import dev.imabad.theatrical.api.CableType;
import dev.imabad.theatrical.graphs.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class SyncCableNetwork extends CableNetworkPacket {
    public Map<Integer, CableNodePos> addedNodes;
    public List<Integer> removedNodes;
    public Map<Integer, UUID> splitSubNetworks;
    public boolean fullWipe;

    public SyncCableNetwork(UUID networkId, CableType networkType){
        this.networkId = networkId;
        this.networkType = networkType;
        addedNodes = new HashMap<>();
        removedNodes = new ArrayList<>();
        splitSubNetworks = new HashMap<>();
        packetDeletesNetwork = false;
    }

    SyncCableNetwork(FriendlyByteBuf buf){
        networkId = buf.readUUID();
        networkType = CableType.valueOf(buf.readUtf());
        packetDeletesNetwork = buf.readBoolean();
        fullWipe = buf.readBoolean();

        if(packetDeletesNetwork){
            return;
        }

        DimensionMap dimensionMap = DimensionMap.fromBuffer(buf);

        addedNodes = new HashMap<>();
        removedNodes = new ArrayList<>();
        splitSubNetworks = new HashMap<>();
        int size;

        size = buf.readVarInt();
        for(int i = 0; i < size; i++){
            removedNodes.add(buf.readVarInt());
        }

        size = buf.readVarInt();
        for(int i = 0; i < size; i++){
            int index = buf.readVarInt();
            CableNodePos cableNodePos = CableNodePos.fromBuffer(buf, dimensionMap);
            addedNodes.put(index, cableNodePos);
        }

        size = buf.readVarInt();
        for(int i = 0; i < size; i++){
            splitSubNetworks.put(buf.readVarInt(), buf.readUUID());
        }
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(networkId);
        buf.writeUtf(networkType.name());
        buf.writeBoolean(packetDeletesNetwork);
        buf.writeBoolean(fullWipe);

        if(packetDeletesNetwork){
            return;
        }

        DimensionMap dimensionMap = new DimensionMap();
        addedNodes.forEach((node, pos) -> dimensionMap.map(pos.dimension()));
        dimensionMap.toBuffer(buf);

        buf.writeVarInt(removedNodes.size());
        removedNodes.forEach(buf::writeVarInt);

        buf.writeVarInt(addedNodes.size());
        addedNodes.forEach((node, pos) -> {
            buf.writeVarInt(node);
            pos.toBuf(buf, dimensionMap);
        });

        buf.writeVarInt(splitSubNetworks.size());
        splitSubNetworks.forEach((node, uuid) -> {
            buf.writeVarInt(node);
            buf.writeUUID(uuid);
        });
    }

    @Override
    public MessageType getType() {
        return TheatricalNet.SYNC_CABLE_NETWORK;
    }

    @Override
    protected void handle(GlobalCableManager manager, CableNetwork network) {
        if(packetDeletesNetwork){
            manager.removeNetwork(network);
            return;
        }

        if(fullWipe){
            manager.removeNetwork(network);
            network = manager.getOrCreateNetwork(networkId, networkType);
        }

        for(int nodeId: removedNodes){
            CableNode node = network.getNodeById(nodeId);
            if(node != null){
                network.removeNode(null, node.getPosition());
            }
        }

        for (Map.Entry<Integer, CableNodePos> entry : addedNodes.entrySet()) {
            Integer nodeId = entry.getKey();
            CableNodePos nodePos = entry.getValue();
            network.loadNode(nodePos, nodeId);
        }

        if(!splitSubNetworks.isEmpty()){
            network.findDisconnectedNetworks(null, splitSubNetworks)
                    .forEach(manager::putNetwork);
        }
    }
}
