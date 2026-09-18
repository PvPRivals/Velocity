/*
 * Copyright (C) 2026 Velocity Contributors
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
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

/**
 * An encode-only player absorption metadata update for Minecraft 1.8.
 */
public final class LegacyPlayerAbsorptionPacket implements MinecraftPacket {

  private final int entityId;
  private final float absorption;

  public LegacyPlayerAbsorptionPacket() {
    this(0, 0.0F);
  }

  public LegacyPlayerAbsorptionPacket(int entityId, float absorption) {
    this.entityId = entityId;
    this.absorption = absorption;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    throw new UnsupportedOperationException("Decode is not implemented");
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction, ProtocolVersion protocolVersion) {
    ProtocolUtils.writeVarInt(buf, entityId);
    buf.writeByte((3 << 5) | 17); // Float metadata at the 1.8 player absorption index.
    buf.writeFloat(absorption);
    buf.writeByte(0x7f);
  }

  @Override
  public int encodeSizeHint(ProtocolUtils.Direction direction, ProtocolVersion version) {
    return ProtocolUtils.varIntBytes(entityId) + 6;
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }
}
