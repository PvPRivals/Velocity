/*
 * Copyright (C) 2024 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.protocol.packet;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils.Direction;
import io.netty.buffer.ByteBuf;

/**
 * Represents a packet used to confirm a transaction
 * between the client and the server.
 *
 * <p>In Minecraft versions prior to 1.17, this packet was commonly used for
 * transaction acknowledgments (window confirmations). From 1.17 onwards,
 * the protocol was simplified to a single integer-based ping/pong mechanism.</p>
 */
public class ServerPingPacket implements MinecraftPacket {

  private byte windowId;
  private int action;
  private boolean accepted;

  public ServerPingPacket() {
  }

  /**
   * Constructs a new {@code ServerPingPacket}.
   *
   * <p>Note: The {@code windowId} and {@code accepted} parameters are only utilized
   * in protocol versions prior to Minecraft 1.17 (where this packet acted as
   * a Transaction Confirmation). For 1.17 and later, only the {@code action}
   * (acting as the Ping ID) is used.</p>
   *
   * @param windowId the ID of the window being confirmed (pre-1.17)
   * @param action   the unique action number or ping ID
   * @param accepted {@code true} if the transaction was accepted, {@code false} otherwise (pre-1.17)
   */
  public ServerPingPacket(byte windowId, int action, boolean accepted) {
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
