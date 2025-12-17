package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;

public class ServerPingPacket implements MinecraftPacket {

  private byte windowId;
  private int action;
  private boolean accepted;

  public ServerPingPacket() {
  }

  public ServerPingPacket(byte windowId, short action, boolean accepted) {
    this.windowId = windowId;
    this.action = action;
    this.accepted = accepted;
  }

  public int getAction() {
    return action;
  }

  @Override
  public void decode(ByteBuf buf, Direction direction, ProtocolVersion protocolVersion) {
    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_17)) {
      windowId = buf.readByte();
    }
    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_17)) {
      action = buf.readShort();
    } else {
      action = buf.readInt();
    }
    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_17)) {
      accepted = buf.readBoolean();
    }
  }

  @Override
  public void encode(ByteBuf buf, Direction direction, ProtocolVersion protocolVersion) {
    if (protocolVersion.lessThan(ProtocolVersion.MINECRAFT_1_17)) {
      buf.writeByte(windowId);
      buf.writeShort(action);
      buf.writeBoolean(accepted);
    } else {
      buf.writeInt(action);
    }
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

}
