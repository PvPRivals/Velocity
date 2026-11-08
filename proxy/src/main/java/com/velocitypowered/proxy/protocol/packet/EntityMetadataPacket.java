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

import com.google.common.base.Preconditions;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates values of an entity's metadata, such as a player's absorption.
 *
 * <p>Implemented for Minecraft 1.8 to 1.19.2; 1.19.3 numbers the value types differently. Only the
 * value types of {@link Entry} are supported, not items, text components or the types added
 * later.</p>
 */
public final class EntityMetadataPacket implements MinecraftPacket {

  // 1.8 packs the type and index into one byte
  private static final int LEGACY_MAX_INDEX = 0x1F;
  private static final int LEGACY_TYPE_SHIFT = 5;
  private static final int LEGACY_END = 0x7F;
  // 1.9 writes the index as a byte and the type as a VarInt
  private static final int MAX_INDEX = 0xFE;
  private static final int END = 0xFF;

  private int entityId;
  private List<Entry> entries;

  public EntityMetadataPacket() {
    this(0, List.of());
  }

  /**
   * Creates a packet that updates the given values of an entity.
   *
   * @param entityId the entity to update
   * @param entries the values to update
   */
  public EntityMetadataPacket(int entityId, List<Entry> entries) {
    this.entityId = entityId;
    this.entries = List.copyOf(entries);
  }

  public int getEntityId() {
    return entityId;
  }

  public List<Entry> getEntries() {
    return entries;
  }

  @Override
  public void decode(ByteBuf buf, ProtocolUtils.Direction direction,
      ProtocolVersion protocolVersion) {
    checkVersion(protocolVersion);
    this.entityId = ProtocolUtils.readVarInt(buf);

    List<Entry> entries = new ArrayList<>();
    if (isLegacy(protocolVersion)) {
      int header;
      while ((header = buf.readUnsignedByte()) != LEGACY_END) {
        int typeId = header >> LEGACY_TYPE_SHIFT;
        entries.add(readValue(buf, protocolVersion, header & LEGACY_MAX_INDEX,
            type(typeId, protocolVersion)));
      }
    } else {
      int index;
      while ((index = buf.readUnsignedByte()) != END) {
        int typeId = ProtocolUtils.readVarInt(buf);
        entries.add(readValue(buf, protocolVersion, index, type(typeId, protocolVersion)));
      }
    }
    this.entries = List.copyOf(entries);
  }

  private static Entry readValue(ByteBuf buf, ProtocolVersion version, int index, Type type) {
    return switch (type) {
      case BYTE -> new ByteEntry(index, buf.readByte());
      case SHORT -> new ShortEntry(index, buf.readShort());
      case INT -> new IntEntry(index,
          isLegacy(version) ? buf.readInt() : ProtocolUtils.readVarInt(buf));
      case FLOAT -> new FloatEntry(index, buf.readFloat());
      case STRING -> new StringEntry(index, ProtocolUtils.readString(buf));
      case BOOLEAN -> new BooleanEntry(index, buf.readBoolean());
      case POSITION -> isLegacy(version)
          ? new PositionEntry(index, buf.readInt(), buf.readInt(), buf.readInt())
          : readPosition(buf, version, index);
      case ROTATION -> new RotationEntry(index, buf.readFloat(), buf.readFloat(),
          buf.readFloat());
    };
  }

  @Override
  public void encode(ByteBuf buf, ProtocolUtils.Direction direction,
      ProtocolVersion protocolVersion) {
    checkVersion(protocolVersion);
    ProtocolUtils.writeVarInt(buf, entityId);

    boolean legacy = isLegacy(protocolVersion);
    for (Entry entry : entries) {
      Type type = Type.of(entry);
      int typeId = type.id(protocolVersion);
      if (typeId < 0) {
        throw new IllegalArgumentException(type + " metadata does not exist in " + protocolVersion);
      }

      if (legacy) {
        Preconditions.checkArgument(entry.index() <= LEGACY_MAX_INDEX,
            "Metadata index %s is above %s in %s", entry.index(), LEGACY_MAX_INDEX,
            protocolVersion);
        buf.writeByte(typeId << LEGACY_TYPE_SHIFT | entry.index());
      } else {
        buf.writeByte(entry.index());
        ProtocolUtils.writeVarInt(buf, typeId);
      }

      switch (entry) {
        case ByteEntry e -> buf.writeByte(e.value());
        case ShortEntry e -> buf.writeShort(e.value());
        case IntEntry e -> {
          if (legacy) {
            buf.writeInt(e.value());
          } else {
            ProtocolUtils.writeVarInt(buf, e.value());
          }
        }
        case FloatEntry e -> buf.writeFloat(e.value());
        case StringEntry e -> ProtocolUtils.writeString(buf, e.value());
        case BooleanEntry e -> buf.writeBoolean(e.value());
        case PositionEntry e -> {
          if (legacy) {
            buf.writeInt(e.x());
            buf.writeInt(e.y());
            buf.writeInt(e.z());
          } else {
            writePosition(buf, protocolVersion, e);
          }
        }
        case RotationEntry e -> {
          buf.writeFloat(e.x());
          buf.writeFloat(e.y());
          buf.writeFloat(e.z());
        }
      }
    }
    buf.writeByte(legacy ? LEGACY_END : END);
  }

  // 26 bits for x and z and 12 for y. 1.14 moved y to the lowest bits.
  private static void writePosition(ByteBuf buf, ProtocolVersion version, PositionEntry position) {
    long x = position.x() & 0x3FFFFFFL;
    long y = position.y() & 0xFFFL;
    long z = position.z() & 0x3FFFFFFL;
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_14)) {
      buf.writeLong(x << 38 | z << 12 | y);
    } else {
      buf.writeLong(x << 38 | y << 26 | z);
    }
  }

  private static PositionEntry readPosition(ByteBuf buf, ProtocolVersion version, int index) {
    long packed = buf.readLong();
    int x = (int) (packed >> 38);
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_14)) {
      return new PositionEntry(index, x, (int) (packed << 52 >> 52), (int) (packed << 26 >> 38));
    }
    return new PositionEntry(index, x, (int) (packed << 26 >> 52), (int) (packed << 38 >> 38));
  }

  private static boolean isLegacy(ProtocolVersion version) {
    return version.lessThan(ProtocolVersion.MINECRAFT_1_9);
  }

  private static void checkVersion(ProtocolVersion version) {
    if (version.lessThan(ProtocolVersion.MINECRAFT_1_8)
        || version.noLessThan(ProtocolVersion.MINECRAFT_1_19_3)) {
      throw new UnsupportedOperationException(
          "Entity metadata is only implemented for Minecraft 1.8 to 1.19.2, not " + version);
    }
  }

  private static Type type(int typeId, ProtocolVersion version) {
    for (Type type : Type.values()) {
      if (type.id(version) == typeId) {
        return type;
      }
    }
    throw new DecoderException("Unsupported metadata type " + typeId + " in " + version);
  }

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    return handler.handle(this);
  }

  @Override
  public String toString() {
    return "EntityMetadataPacket{"
        + "entityId=" + entityId
        + ", entries=" + entries
        + '}';
  }

  /**
   * The value types of the entries, with their ids. 1.9 dropped shorts, added booleans and wrote
   * positions as a long; 1.13 added optional text components before booleans.
   */
  private enum Type {
    BYTE(0, 0, 0),
    SHORT(1, -1, -1),
    INT(2, 1, 1),
    FLOAT(3, 2, 2),
    STRING(4, 3, 3),
    BOOLEAN(-1, 6, 7),
    POSITION(6, 8, 9),
    ROTATION(7, 7, 8);

    private final int legacyId;
    private final int id1x9;
    private final int id1x13;

    Type(int legacyId, int id1x9, int id1x13) {
      this.legacyId = legacyId;
      this.id1x9 = id1x9;
      this.id1x13 = id1x13;
    }

    /**
     * Returns the type's id in the given version, or -1 if the version has no such type.
     */
    int id(ProtocolVersion version) {
      if (isLegacy(version)) {
        return legacyId;
      }
      return version.noLessThan(ProtocolVersion.MINECRAFT_1_13) ? id1x13 : id1x9;
    }

    static Type of(Entry entry) {
      return switch (entry) {
        case ByteEntry e -> BYTE;
        case ShortEntry e -> SHORT;
        case IntEntry e -> INT;
        case FloatEntry e -> FLOAT;
        case StringEntry e -> STRING;
        case BooleanEntry e -> BOOLEAN;
        case PositionEntry e -> POSITION;
        case RotationEntry e -> ROTATION;
      };
    }
  }

  /**
   * One value of an entity's metadata, at its index.
   */
  public sealed interface Entry {

    /**
     * Returns where the value is in the entity's metadata: 0 to 31 in 1.8, 0 to 254 later.
     *
     * @return the index
     */
    int index();

    private static void checkIndex(int index) {
      Preconditions.checkArgument(index >= 0 && index <= MAX_INDEX,
          "Metadata index %s is not between 0 and %s", index, MAX_INDEX);
    }
  }

  /**
   * A byte value, such as an entity's flags.
   *
   * @param index where the value is
   * @param value the value
   */
  public record ByteEntry(int index, byte value) implements Entry {

    public ByteEntry {
      Entry.checkIndex(index);
    }
  }

  /**
   * A short value, such as an entity's remaining air. Only exists in 1.8.
   *
   * @param index where the value is
   * @param value the value
   */
  public record ShortEntry(int index, short value) implements Entry {

    public ShortEntry {
      Entry.checkIndex(index);
    }
  }

  /**
   * An int value, such as a player's score. A VarInt from 1.9.
   *
   * @param index where the value is
   * @param value the value
   */
  public record IntEntry(int index, int value) implements Entry {

    public IntEntry {
      Entry.checkIndex(index);
    }
  }

  /**
   * A float value, such as an entity's health or a player's absorption.
   *
   * @param index where the value is
   * @param value the value
   */
  public record FloatEntry(int index, float value) implements Entry {

    public FloatEntry {
      Entry.checkIndex(index);
    }
  }

  /**
   * A string value, such as an entity's custom name before 1.13.
   *
   * @param index where the value is
   * @param value the value
   */
  public record StringEntry(int index, String value) implements Entry {

    public StringEntry {
      Entry.checkIndex(index);
      Preconditions.checkNotNull(value, "value");
    }
  }

  /**
   * A boolean value, such as whether an entity is silent. Only exists from 1.9.
   *
   * @param index where the value is
   * @param value the value
   */
  public record BooleanEntry(int index, boolean value) implements Entry {

    public BooleanEntry {
      Entry.checkIndex(index);
    }
  }

  /**
   * A block position value.
   *
   * @param index where the value is
   * @param x the block's x coordinate
   * @param y the block's y coordinate
   * @param z the block's z coordinate
   */
  public record PositionEntry(int index, int x, int y, int z) implements Entry {

    public PositionEntry {
      Entry.checkIndex(index);
    }
  }

  /**
   * A rotation value, such as an armor stand's head pose.
   *
   * @param index where the value is
   * @param x the rotation around the x axis
   * @param y the rotation around the y axis
   * @param z the rotation around the z axis
   */
  public record RotationEntry(int index, float x, float y, float z) implements Entry {

    public RotationEntry {
      Entry.checkIndex(index);
    }
  }
}
